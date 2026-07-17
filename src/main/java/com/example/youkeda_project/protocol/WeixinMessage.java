package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WeixinMessage(
    @JsonProperty("from_user_id") String fromUserId,
    @JsonProperty("to_user_id") String toUserId,
    @JsonProperty("message_type") int messageType,
    @JsonProperty("message_state") int messageState,
    @JsonProperty("context_token") String contextToken,
    @JsonProperty("item_list") List<MessageItem> itemList
) {}
