package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SendMessageRequest(
    Msg msg,
    @JsonProperty("base_info") Map<String, Object> baseInfo
) {
    private static final Map<String, Object> BASE_INFO = Map.of("channel_version", "1.0.2");

    public static SendMessageRequest items(String toUserId, String contextToken, List<MessageItem> itemList) {
        return new SendMessageRequest(
            new Msg("", toUserId, UUID.randomUUID().toString(), 2, 2, contextToken, itemList),
            BASE_INFO
        );
    }

    public static SendMessageRequest text(String toUserId, String contextToken, String text) {
        return items(toUserId, contextToken, List.of(MessageItem.text(text)));
    }

    public static SendMessageRequest cdnImage(String toUserId, String contextToken, CDNMedia media, int midSize) {
        return items(toUserId, contextToken, List.of(MessageItem.cdnImage(media, midSize)));
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
