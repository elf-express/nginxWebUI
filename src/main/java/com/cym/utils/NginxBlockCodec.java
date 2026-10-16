package com.cym.utils;

import com.cym.ext.NginxBlockDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Structural validation only. Module availability and directive semantics require nginx -t. */
public final class NginxBlockCodec {
    private static final Set<String> CONTEXTS = Set.of("http-upstream", "stream-upstream", "stream-child");
    private static final Set<String> CONTAINERS = Set.of("http", "stream", "events", "upstream", "location", "if");

    private NginxBlockCodec() { }

    public static NginxBlockDraft parseSingleBlock(String text, String context) {
        text = text == null ? "" : text;
        if (!CONTEXTS.contains(context == null ? "" : context)) throw error("INVALID_CONTEXT", text, 0);
        Scan scan = scan(text);
        List<Token> pending = new ArrayList<>();
        int depth = 0, open = -1, close = -1;
        String header = null;
        for (Token token : scan.tokens) {
            if (close >= 0) throw error(token.kind == '}' ? "UNBALANCED_BRACES" : "MULTIPLE_BLOCKS", text, token.start);
            if (token.kind == 'w') {
                pending.add(token);
            } else if (token.kind == '{') {
                if (pending.isEmpty()) throw error("EXPECTED_BLOCK", text, token.start);
                checkContext(pending, context, depth, text);
                if (depth == 0) {
                    open = token.start;
                    StringBuilder value = new StringBuilder();
                    for (Token word : pending) {
                        if (value.length() > 0) value.append(' ');
                        value.append(word.raw);
                    }
                    header = value.toString();
                }
                pending.clear();
                depth++;
            } else if (token.kind == ';') {
                if (depth == 0) throw error("EXPECTED_BLOCK", text, token.start);
                pending.clear();
            } else {
                if (depth == 0) throw error("UNBALANCED_BRACES", text, token.start);
                if (!pending.isEmpty()) throw error("MISSING_TERMINATOR", text, token.start);
                if (--depth == 0) close = token.start;
            }
        }
        if (depth != 0) throw error("UNBALANCED_BRACES", text, text.length());
        if (open < 0) throw error("EXPECTED_BLOCK", text, 0);
        return new NginxBlockDraft(header, text.substring(open + 1, close));
    }

    public static void validate(String header, String body, String context) {
        compose(header, body, context);
    }

    public static String render(String header, String body, String context) {
        return compose(header, body, context);
    }

    private static String compose(String header, String body, String context) {
        header = header == null ? "" : header.trim();
        body = body == null ? "" : body;
        Scan headerScan = scan(header);
        for (Token token : headerScan.tokens) {
            if (token.kind != 'w') throw error("INVALID_HEADER", header, token.start);
        }
        // A newline is required only when the last comment would consume the generated brace.
        String prefix = header + (headerScan.trailingComment ? "\n{" : " {");
        Scan bodyScan = scan(body);
        String result = prefix + body + (bodyScan.trailingComment ? "\n}" : "}");
        NginxBlockDraft parsed = parseSingleBlock(result, context);
        if (!parsed.getBody().equals(body + (bodyScan.trailingComment ? "\n" : ""))) {
            throw error("MULTIPLE_BLOCKS", result, prefix.length());
        }
        return result;
    }

    private static void checkContext(List<Token> words, String context, int depth, String text) {
        String directive = words.get(0).value;
        int position = words.get(0).start;
        if (depth == 0 && !"stream-child".equals(context)) {
            if (!"upstream".equals(directive)) throw error("WRONG_CONTEXT", text, position);
            if (words.size() != 2) throw error("INVALID_NAME", text, position);
            String name = words.get(1).value;
            if (name.isEmpty() || words.get(1).raw.indexOf('\\') >= 0 || name.chars().anyMatch(c -> Character.isWhitespace(c) || c == '/' || c == '\\')) {
                throw error("INVALID_NAME", text, words.get(1).start);
            }
        } else if (CONTAINERS.contains(directive) || (depth > 0 && "server".equals(directive))) {
            throw error("WRONG_CONTEXT", text, position);
        }
    }

    private static Scan scan(String text) {
        Scan result = new Scan();
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (ch == '#') {
                while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++;
                result.trailingComment = i == text.length();
                continue;
            }
            if (ch == '{' || ch == '}' || ch == ';') {
                result.tokens.add(new Token(ch, i, String.valueOf(ch), String.valueOf(ch)));
                i++;
                continue;
            }
            int start = i;
            StringBuilder value = new StringBuilder();
            char quote = 0;
            int quoteStart = -1;
            while (i < text.length()) {
                ch = text.charAt(i);
                if (ch == '\\') {
                    if (i + 1 == text.length()) throw error("DANGLING_ESCAPE", text, i);
                    value.append(text.charAt(i + 1));
                    i += 2;
                } else if (quote != 0) {
                    if (ch == quote) quote = 0;
                    else value.append(ch);
                    i++;
                } else if (ch == '\'' || ch == '"') {
                    quote = ch;
                    quoteStart = i++;
                } else if (ch == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    int end = text.indexOf('}', i + 2);
                    if (end < 0) throw error("UNBALANCED_BRACES", text, i + 1);
                    value.append(text, i, end + 1);
                    i = end + 1;
                } else if (Character.isWhitespace(ch) || ch == '{' || ch == '}' || ch == ';') {
                    break;
                } else {
                    value.append(ch);
                    i++;
                }
            }
            if (quote != 0) throw error("UNTERMINATED_QUOTE", text, quoteStart);
            result.tokens.add(new Token('w', start, text.substring(start, i), value.toString()));
        }
        return result;
    }

    private static BlockException error(String code, String text, int offset) {
        int line = 1, column = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            char ch = text.charAt(i);
            if (ch == '\r') {
                if (i + 1 < offset && text.charAt(i + 1) == '\n') i++;
                line++;
                column = 1;
            } else if (ch == '\n') { line++; column = 1; }
            else column++;
        }
        return new BlockException(code, line, column);
    }

    private static class Scan {
        final List<Token> tokens = new ArrayList<>();
        boolean trailingComment;
    }
    private record Token(char kind, int start, String raw, String value) { }

    /** Stable codes for controller localization; line/column are one-based in the supplied text. */
    public static class BlockException extends IllegalArgumentException {
        private final String code;
        private final int line;
        private final int column;
        private BlockException(String code, int line, int column) {
            super(code + " at " + line + ":" + column);
            this.code = code;
            this.line = line;
            this.column = column;
        }
        public String getCode() { return code; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }
}
