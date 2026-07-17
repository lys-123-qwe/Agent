package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QRCodeResponse(
    String qrcode,
    @JsonProperty("qrcode_img_content") String qrcodeImgContent
) {}
