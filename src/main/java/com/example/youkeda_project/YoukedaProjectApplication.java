package com.example.youkeda_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Claw 助手入口.
 * <p>
 * 启动后进入 CLI 模式,自动弹出 QR 码等待扫码登录,
 * 登录成功后进入消息轮询模式,所有功能通过微信消息或 CLI 命令触发.
 * <p>
 * 支持多 Bot 同时登录(wechat login <label>).
 */
@SpringBootApplication
@EnableAsync
public class YoukedaProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoukedaProjectApplication.class, args);
    }
}
