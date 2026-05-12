package model;

import java.time.LocalDateTime;

public class GroupMessage {
    private int id;
    private int groupId;
    private String sender;
    private String content;
    private LocalDateTime timestamp;
    private boolean seen;
    private String type; // "TEXT", "FILE"
    private String filePath;

    public GroupMessage() {}

    public GroupMessage(int groupId, String sender, String content) {
        this.groupId = groupId;
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.seen = false;
        this.type = "TEXT";
    }

    // Getters et Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}