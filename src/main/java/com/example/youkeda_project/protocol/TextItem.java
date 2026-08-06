package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信消息中的文本项.
 */

public record TextItem(
    @JsonProperty("text") String text
) {}
