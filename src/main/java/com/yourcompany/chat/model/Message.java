package com.yourcompany.chat.model;

import java.time.LocalDateTime;

public class Message {
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    private String receiver; // optional - for 1:1 chat

    public Message(String sender, String content, String receiver) {
        this.sender = sender;
        this.content = content;
        this.receiver = receiver;
        this.timestamp = LocalDateTime.now();
    }

    public String getSender() { return sender; }
    public String getContent() { return content; }
    public String getReceiver() { return receiver; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
