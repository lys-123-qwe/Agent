package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ImageItem(
    @JsonProperty("image_url") String imageUrl,
    Integer width,
    Integer height
) {
    public ImageItem(String imageUrl) {
        this(imageUrl, null, null);
    }
}
