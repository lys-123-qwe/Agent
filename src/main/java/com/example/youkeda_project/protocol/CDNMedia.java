package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CDNMedia(
    @JsonProperty("encrypt_query_param") String encryptQueryParam,
    @JsonProperty("full_url") String fullUrl,
    @JsonProperty("aes_key") String aesKey,
    @JsonProperty("encrypt_type") Integer encryptType
) {
    public CDNMedia(String encryptQueryParam, String aesKey) {
        this(encryptQueryParam, null, aesKey, 1);
    }
}
