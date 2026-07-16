package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageItem {

    private int type;

    @JsonProperty("text_item")
    private TextItem textItem;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public TextItem getTextItem() {
        return textItem;
    }

    public void setTextItem(TextItem textItem) {
        this.textItem = textItem;
    }
}