package com.example.youkeda_project.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserContextStore {

    private final ConcurrentHashMap<String, String> contextTokens = new ConcurrentHashMap<>();

    public String getToken(String userId) {
        return contextTokens.get(userId);
    }
}
