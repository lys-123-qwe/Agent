package com.example.youkeda_project.entity;

public class ChatHistory {
    private Long id;
    private String userId;
    private String role;
    private String content;
    private String toolCallId;
    private String name;
    private String toolCalls;
    private String createdAt;

    public ChatHistory() {}

    public ChatHistory(String userId, String role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getToolCalls() { return toolCalls; }
    public void setToolCalls(String toolCalls) { this.toolCalls = toolCalls; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ChatHistory{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", role='" + role + '\'' +
                ", content='" + content + '\'' +
                ", toolCallId='" + toolCallId + '\'' +
                ", name='" + name + '\'' +
                ", toolCalls='" + toolCalls + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
