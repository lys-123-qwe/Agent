package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WeixinMessage {

    @JsonProperty("from_user_id")
    private String fromUserId;

    @JsonProperty("to_user_id")
    private String toUserId;

    @JsonProperty("message_type")
    private int messageType;

    @JsonProperty("message_state")
    private int messageState;

    @JsonProperty("context_token")
    private String contextToken;

    @JsonProperty("item_list")
    private List<MessageItem> itemList;

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public int getMessageState() {
        return messageState;
    }

    public void setMessageState(int messageState) {
        this.messageState = messageState;
    }

    public String getContextToken() {
        return contextToken;
    }

    public void setContextToken(String contextToken) {
        this.contextToken = contextToken;
    }

    public List<MessageItem> getItemList() {
        return itemList;
    }

    public void setItemList(List<MessageItem> itemList) {
        this.itemList = itemList;
    }
}