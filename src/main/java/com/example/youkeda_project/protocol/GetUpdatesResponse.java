package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * iLink getUpdates 接口的响应体.
 * <p>
 * ret: 返回码,0 表示成功;
 * msgs: 新消息列表;
 * getUpdatesBuf: 游标,下次请求时带回以获取后续消息.
 */

public record GetUpdatesResponse(
    Integer ret,
    List<WeixinMessage> msgs,
    @JsonProperty("get_updates_buf") String getUpdatesBuf
) {}
