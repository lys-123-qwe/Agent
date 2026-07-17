package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record SendMessageRequest(Msg msg) {

    public static SendMessageRequest text(String toUserId, String contextToken, String text) {
        return new SendMessageRequest(new Msg("", toUserId, UUID.randomUUID().toString(),
                2, 2, contextToken, List.of(MessageItem.text(text))));
    }

    public static SendMessageRequest image(String toUserId, String contextToken, String imageUrl) {
        return new SendMessageRequest(new Msg("", toUserId, UUID.randomUUID().toString(),
                2, 2, contextToken, List.of(MessageItem.image(imageUrl))));
    }

    public static SendMessageRequest items(String toUserId, String contextToken, List<MessageItem> itemList) {
        return new SendMessageRequest(new Msg("", toUserId, UUID.randomUUID().toString(),
                2, 2, contextToken, itemList));
    }

    public record Msg(
        @JsonProperty("from_user_id") String fromUserId,
        @JsonProperty("to_user_id") String toUserId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("message_type") int messageType,
        @JsonProperty("message_state") int messageState,
        @JsonProperty("context_token") String contextToken,
        @JsonProperty("item_list") List<MessageItem> itemList
    ) {}
}
