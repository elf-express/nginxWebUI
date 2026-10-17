package com.cym.utils;

import com.cym.ext.NginxBlockDraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NginxBlockCodecTest {
    @Test void singleUpstreamRoundTrip() {
        String body = "\r\n    least_conn;\r\n    server 127.0.0.1:8080;\r\n    server 127.0.0.2:8080;\r\n";
        NginxBlockDraft draft = NginxBlockCodec.parseSingleBlock("upstream website_api {" + body + "}", "http-upstream");
        assertEquals("upstream website_api", draft.getHeader());
        assertEquals(body, draft.getBody());
        assertEquals("upstream website_api {" + body + "}", NginxBlockCodec.render(draft.getHeader(), draft.getBody(), "http-upstream"));
    }
    @Test void quotedBracesAndComments() {
        String body = "\n # } { ignored\n custom \"a\\\"}#;\" 'x{';\n custom escaped\\{ escaped\\#;\n";
        assertEquals(body, NginxBlockCodec.parseSingleBlock("# prefix\nserver {" + body + "} # suffix", "stream-child").getBody());
    }
    @Test void mapBodyAndLegalNestedBlock() {
        String body = "\n hostnames;\n ~^www\\.example ${backend};\n default \"a{b}\";\n";
        assertEquals(body, NginxBlockCodec.parseSingleBlock("map $name $result {" + body + "}", "stream-child").getBody());
        assertDoesNotThrow(() -> NginxBlockCodec.validate("custom_module arg", " nested_custom { value on; }", "stream-child"));
    }
    @Test void hashInsideUnquotedTokenIsNotAComment() {
        String body = "custom abc#def;";
        assertEquals(body, NginxBlockCodec.parseSingleBlock("server {" + body + "}", "stream-child").getBody());
        assertEquals("server {custom abc#def;}", NginxBlockCodec.render("server", body, "stream-child"));
    }
    @Test void closingBraceInsideUnquotedTokenIsNotStructural() {
        String body = " default backend}; ";
        NginxBlockDraft draft = NginxBlockCodec.parseSingleBlock("map $name $result {" + body + "}", "stream-child");
        assertEquals(body, draft.getBody());
        assertEquals("map $name $result { default backend}; }",
                NginxBlockCodec.render(draft.getHeader(), draft.getBody(), "stream-child"));
    }
    @Test void rejectMultipleBlocks() {
        error("MULTIPLE_BLOCKS", "server {} server {}", "stream-child");
        error("MULTIPLE_BLOCKS", "server {} worker_processes 2;", "stream-child");
    }
    @Test void rejectWrongContext() {
        for (String text : new String[]{"stream {}", "http {}", "upstream api {}", "location / {}", "server { location / {} }", "server { stream {} }"}) {
            error("WRONG_CONTEXT", text, "stream-child");
        }
        error("WRONG_CONTEXT", "server {}", "http-upstream");
        error("WRONG_CONTEXT", "upstream api { upstream other {} }", "stream-upstream");
        error("INVALID_CONTEXT", "server {}", "unknown");
    }
    @Test void rejectPathName() {
        for (String name : new String[]{"../api", "a/b", "a\\b", "a b", "\"a b\"", ""}) {
            error("INVALID_NAME", "upstream " + name + " {}", "http-upstream");
        }
    }
    @Test void allowRepeatedServerHeaders() {
        for (int i = 0; i < 3; i++) assertDoesNotThrow(() -> NginxBlockCodec.validate("server", "listen 9000;", "stream-child"));
        assertDoesNotThrow(() -> NginxBlockCodec.validate("upstream api", "custom_balancer on;", "stream-upstream"));
    }
    @Test void rejectMalformedStructure() {
        error("UNBALANCED_BRACES", "server {", "stream-child");
        error("UNBALANCED_BRACES", "server {}}", "stream-child");
        error("UNTERMINATED_QUOTE", "server { x \"oops; }", "stream-child");
        error("MISSING_TERMINATOR", "server { listen 80 }", "stream-child");
        error("EXPECTED_BLOCK", "server;", "stream-child");
        error("EXPECTED_BLOCK", "", "stream-child");
        assertThrows(NginxBlockCodec.BlockException.class, () -> NginxBlockCodec.validate("server {}", "", "stream-child"));
        assertThrows(NginxBlockCodec.BlockException.class, () -> NginxBlockCodec.validate("server", "} server {", "stream-child"));
    }
    @Test void errorLocationUsesOriginalCrlfText() {
        NginxBlockCodec.BlockException ex = error("UNTERMINATED_QUOTE", "server {\r\n  x \"oops", "stream-child");
        assertEquals(2, ex.getLine());
        assertEquals(5, ex.getColumn());
    }
    @Test void trailingCommentCannotSwallowClosingBrace() {
        String rendered = NginxBlockCodec.render("server", "listen 80; # comment", "stream-child");
        assertEquals("server {listen 80; # comment\n}", rendered);
    }
    private NginxBlockCodec.BlockException error(String code, String text, String context) {
        NginxBlockCodec.BlockException ex = assertThrows(NginxBlockCodec.BlockException.class,
                () -> NginxBlockCodec.parseSingleBlock(text, context));
        assertEquals(code, ex.getCode());
        assertTrue(ex.getLine() > 0);
        assertTrue(ex.getColumn() > 0);
        return ex;
    }
}
