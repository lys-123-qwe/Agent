package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信 CDN 媒体文件元数据.
 * <p>
 * encryptQueryParam: CDN 下载/上传用的加密参数;
 * fullUrl: 文件直接下载地址(可选);
 * aesKey: 媒体文件的 AES 加密密钥(hex 或 base64);
 * encrypt_type: 加密方式 1=AES-ECB.
 * <p>
 * 简化构造器:仅传 encryptQueryParam + aesKey,默认 fullUrl=null, encryptType=1.
 */

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
