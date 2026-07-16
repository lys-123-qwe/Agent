package com.example.youkeda_project;

import com.example.youkeda_project.command.CommandHandler;
import com.example.youkeda_project.service.WeatherService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Scanner;

@SpringBootApplication
public class YoukedaProjectApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(YoukedaProjectApplication.class, args);

        CommandHandler commandHandler = context.getBean(CommandHandler.class);
        WeatherService weatherService = context.getBean(WeatherService.class);

        System.out.println("=== Claw 助手已启动 ===");
        System.out.println("输入 help 查看可用命令，输入 exit 退出\n");

        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNextLine()) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if ("exit".equalsIgnoreCase(input)) {
                System.out.println("再见!");
                break;
            }

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