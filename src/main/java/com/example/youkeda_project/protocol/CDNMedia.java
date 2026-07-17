package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CDNMedia(
    @JsonProperty("encrypt_query_param") String encryptQueryParam,
    @JsonProperty("aes_key") String aesKey,
    @JsonProperty("encrypt_type") int encryptType
) {
    public CDNMedia(String encryptQueryParam, String aesKey) {
        this(encryptQueryParam, aesKey, 1);
    }
}
