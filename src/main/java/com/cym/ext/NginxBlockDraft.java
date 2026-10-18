package com.cym.ext;

/** A single block's header and verbatim text between its outer braces. */
public class NginxBlockDraft {
    private String header;
    private String body;

    public NginxBlockDraft() { }
    public NginxBlockDraft(String header, String body) {
        this.header = header;
        this.body = body;
    }
    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
