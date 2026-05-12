package model;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Group {
    private int id;
    private String name;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<GroupMember> members;

    public Group() {
        this.members = new ArrayList<>();
    }

    public Group(String name, String createdBy) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.members = new ArrayList<>();
    }

    // Getters et Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<GroupMember> getMembers() { return members; }
    public void setMembers(List<GroupMember> members) { this.members = members; }

    public void addMember(GroupMember member) {
        this.members.add(member);
    }

    public boolean isMember(String username) {
        return members.stream().anyMatch(m -> m.getUsername().equals(username));
    }
}