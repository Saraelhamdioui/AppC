package model;

import java.time.LocalDateTime;

public class GroupMember {
    private int groupId;
    private String username;
    private String role; // "ADMIN", "MEMBER"
    private LocalDateTime joinedAt;

    public GroupMember() {}

    public GroupMember(int groupId, String username, String role) {
        this.groupId = groupId;
        this.username = username;
        this.role = role;
        this.joinedAt = LocalDateTime.now();
    }

    // Getters et Setters
    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}