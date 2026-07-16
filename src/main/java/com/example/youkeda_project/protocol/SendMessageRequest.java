package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public class SendMessageRequest {

    private Msg msg;

    public SendMessageRequest(String toUserId, String contextToken, String text) {
        TextItem textItem = new TextItem();
        textItem.setText(text);

        MessageItem item = new MessageItem();
        item.setType(1);
        item.setTextItem(textItem);

        this.msg = new Msg();
        msg.setFromUserId("");
        msg.setToUserId(toUserId);
        msg.setClientId(UUID.randomUUID().toString());
        msg.setMessageType(2);
        msg.setMessageState(2);
        msg.setContextToken(contextToken);
        msg.setItemList(List.of(item));
    }

    public Msg getMsg() {
        return msg;
    }

    public void setMsg(Msg msg) {
        this.msg = msg;
    }

    public static class Msg {

        @JsonProperty("from_user_id")
        private String fromUserId;

        @JsonProperty("to_user_id")
        private String toUserId;

        @JsonProperty("client_id")
        private String clientId;

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

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
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
}