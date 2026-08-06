package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 微信消息中的文件项.
 * <p>
 * file_name: 文件名(含扩展名);
 * file_size: 文件大小(字节);
 * file_url: 文件直链下载地址(可选,优先使用 media 构造下载 URL);
 * media: CDN 媒体元数据(包含 encrypt_query_param,aes_key 等).
 */

public record FileItem(
    @JsonProperty("media") CDNMedia media,
    @JsonProperty("file_name") String fileName,
    @JsonProperty("len") String len
) {}
