package com.example.youkeda_project.ai;

import java.util.List;

public class ChatResponse {

    private String id;
    private List<ChatChoice> choices;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ChatChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<ChatChoice> choices) {
        this.choices = choices;
    }
}