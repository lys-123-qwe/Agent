package com.example.youkeda_project.service;

import com.example.youkeda_project.ai.ChatMessage;
import com.example.youkeda_project.entity.ChatHistory;
import com.example.youkeda_project.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class UserContextDao {

    private static final Logger log = LoggerFactory.getLogger(UserContextDao.class);
    private static final int MAX_HISTORY = 10;

    private final ChatHistoryMapper chatHistoryMapper;
    private final ObjectMapper objectMapper;

    public UserContextDao(ChatHistoryMapper chatHistoryMapper, ObjectMapper objectMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存整个对话历史：先删除旧记录，再逐条插入新记录。
     */
    public synchronized void saveContext(String userId, List<ChatMessage> history) {
        if (userId == null || history == null || history.isEmpty()) return;
        try {
            chatHistoryMapper.deleteByUserId(userId);
            for (ChatMessage msg : history) {
                ChatHistory row = new ChatHistory();
                row.setUserId(userId);
                row.setRole(msg.role());

                if (msg.content() instanceof String s) {
                    row.setContent(s);
                } else if (msg.content() != null) {
                    row.setContent(objectMapper.writeValueAsString(msg.content()));
                }

                row.setToolCallId(msg.toolCallId());
                row.setName(msg.name());

                if (msg.toolCalls() != null) {
                    row.setToolCalls(objectMapper.writeValueAsString(msg.toolCalls()));
                }

                chatHistoryMapper.insert(row);
            }
        } catch (Exception e) {
            log.error("保存用户上下文失败: {}", userId, e);
        }
    }

    /**
     * 加载对话历史：按 id 顺序读取所有消息，还原为 ChatMessage 列表。
     */
    public synchronized List<ChatMessage> loadContext(String userId) {
        if (userId == null) return null;
        try {
            List<ChatHistory> rows = chatHistoryMapper.selectByUserId(userId);
            if (rows == null || rows.isEmpty()) return null;

            List<ChatMessage> history = new ArrayList<>();
            for (ChatHistory row : rows) {
                Object content = row.getContent();
                if (content != null) {
                    String s = content.toString();
                    if (s.startsWith("[")) {
                        try {
                            content = objectMapper.readValue(s, List.class);
                        } catch (Exception ignored) {
                        }
                    }
                }

                Object toolCalls = null;
                if (row.getToolCalls() != null) {
                    try {
                        toolCalls = objectMapper.readValue(row.getToolCalls(), List.class);
                    } catch (Exception ignored) {
                    }
                }

                history.add(new ChatMessage(
                    row.getRole(),
                    content,
                    row.getToolCallId(),
                    row.getName(),
                    toolCalls
                ));
            }
            return history;
        } catch (Exception e) {
            log.warn("加载用户上下文失败: {}", userId, e);
        }
        return null;
    }

    public synchronized void deleteContext(String userId) {
        if (userId == null) return;
        try {
            chatHistoryMapper.deleteByUserId(userId);
        } catch (Exception e) {
            log.warn("删除用户上下文失败: {}", userId, e);
        }
    }

    public synchronized boolean hasContext(String userId) {
        if (userId == null) return false;
        try {
            List<ChatHistory> rows = chatHistoryMapper.selectByUserId(userId);
            return rows != null && !rows.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
