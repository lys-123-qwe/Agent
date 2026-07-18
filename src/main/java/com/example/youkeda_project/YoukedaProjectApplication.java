package com.example.youkeda_project;

import com.example.youkeda_project.command.CommandHandler;
import com.example.youkeda_project.service.WeatherService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Scanner;

/**
 * 项目入口 — Claw 助手控制台应用
 * <p>
 * 基于 Spring Boot 的命令行交互程序，支持天气查询和 AI 对话两大功能。
 * 用户在控制台输入 weather <城市名> 查询和风天气，其余消息由 AI 服务回复。
 */
@SpringBootApplication
public class YoukedaProjectApplication {

    /**
     * 启动 Spring 容器，初始化 CommandHandler/WeatherService，
     * 进入控制台交互循环，按行读取用户输入并路由到对应处理器。
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(YoukedaProjectApplication.class, args);

        CommandHandler commandHandler = context.getBean(CommandHandler.class);
        WeatherService weatherService = context.getBean(WeatherService.class);

        System.out.println("=== Claw 助手已启动 ===");
        System.out.println("输入 help 查看可用命令，输入 exit 退出\n");

        // 控制台交互循环，按行读取用户输入
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            // 输入 exit 退出程序
            if ("exit".equalsIgnoreCase(input)) {
                System.out.println("再见!");
                break;
            }

            // weather 前缀的消息走天气查询，其余走 AI 对话
            if (input.toLowerCase().startsWith("weather")) {
                String cityName = input.substring("weather".length()).trim();
                String result = weatherService.queryWeather(cityName);
                System.out.println(result);
            } else {
                String result = commandHandler.handle(input);
                System.out.println(result);
            }
        }

        scanner.close();
        context.close();
    }
}