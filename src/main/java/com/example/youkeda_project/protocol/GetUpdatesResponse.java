package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GetUpdatesResponse(
    int ret,
    List<WeixinMessage> msgs,
    @JsonProperty("get_updates_buf") String getUpdatesBuf
) {}
