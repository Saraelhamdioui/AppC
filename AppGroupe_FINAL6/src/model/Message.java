package model;

import java.time.LocalDateTime;

public class Message {

    private int id;
    private String sender;
    private String receiver;
    private String content;
    private boolean seen;
    private String type;
    private LocalDateTime createdAt;

    public Message(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.seen = false;
        this.type = "TEXT";
        this.createdAt = LocalDateTime.now();
    }

    public Message(int id, String sender, String receiver, String content,
                   boolean seen, String type, LocalDateTime createdAt) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.seen = seen;
        this.type = type;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getContent() {
        return content;
    }

    public String getType() {
        return type;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}