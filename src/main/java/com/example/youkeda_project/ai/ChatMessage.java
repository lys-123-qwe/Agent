package com.example.youkeda_project.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatMessage(
    @JsonProperty("role") String role,
    @JsonProperty("content") Object content
) {}
