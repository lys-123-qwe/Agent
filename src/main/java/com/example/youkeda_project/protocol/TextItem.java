package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TextItem {

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}