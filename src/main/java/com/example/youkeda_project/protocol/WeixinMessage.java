package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * iLink 收到的微信消息结构.
 * <p>
 * fromUserId: 发送方用户 ID;
 * toUserId: 接收方(Bot)ID;
 * contextToken: 回复消息时必须携带的上下文 Token,iLink 协议要求回复时原样传回;
 * itemList: 消息内容列表(可包含文字,图片,语音等多项).
 */
public record WeixinMessage(
    @JsonProperty("from_user_id") String fromUserId,
    @JsonProperty("to_user_id") String toUserId,
    @JsonProperty("message_type") int messageType,
    @JsonProperty("message_state") int messageState,
    @JsonProperty("context_token") String contextToken,
    @JsonProperty("item_list") List<MessageItem> itemList
) {}
