package com.example.youkeda_project.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QRCodeResponse {

    private String qrcode;

    @JsonProperty("qrcode_img_content")
    private String qrcodeImgContent;

    public String getQrcode() {
        return qrcode;
    }

    public void setQrcode(String qrcode) {
        this.qrcode = qrcode;
    }

    public String getQrcodeImgContent() {
        return qrcodeImgContent;
    }

    public void setQrcodeImgContent(String qrcodeImgContent) {
        this.qrcodeImgContent = qrcodeImgContent;
    }
}