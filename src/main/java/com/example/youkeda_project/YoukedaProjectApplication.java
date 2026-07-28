package com.example.youkeda_project;

import com.example.youkeda_project.service.IlinkService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Claw 助手入口。
 * <p>
 * 启动后自动进入 iLink 登录流程（生成二维码等待扫码），
 * 登录成功后进入消息轮询模式，所有功能通过微信消息触发。
 */
@SpringBootApplication
@MapperScan("com.example.youkeda_project.mapper")
public class YoukedaProjectApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(YoukedaProjectApplication.class, args);
        IlinkService ilinkService = context.getBean(IlinkService.class);

        System.out.println("=== Claw 助手已启动 ===\n");

        // 自动开始 iLink 登录，生成二维码等待扫码
        String loginResult = ilinkService.start();
        System.out.println(loginResult);

        if (ilinkService.isConnected()) {
            // 保持主线程存活，让 daemon 轮询线程持续运行
            synchronized (YoukedaProjectApplication.class) {
                try {
                    YoukedaProjectApplication.class.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        context.close();
    }
}
