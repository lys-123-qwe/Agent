package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TextItem(
    @JsonProperty("text") String text
) {}
