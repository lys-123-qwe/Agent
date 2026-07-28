package com.example.youkeda_project.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserContextStore {

    private final ConcurrentHashMap<String, String> contextTokens = new ConcurrentHashMap<>();

    public void updateToken(String userId, String token) {
        if (token != null && !token.isBlank()) {
            contextTokens.put(userId, token);
        }
    }

    public String getToken(String userId) {
        return contextTokens.get(userId);
    }

    public void removeToken(String userId) {
        contextTokens.remove(userId);
    }
}
