package com.example.youkeda_project.command;

import org.springframework.stereotype.Component;

@Component
public class CommandHandler {

    private static final String VERSION = "v1.0.0";

    public String handle(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "请输入命令，输入 help 查看帮助";
        }

        String command = input.trim().toLowerCase();

        try {
            return switch (command) {
                case "help" -> showHelp();
                case "version" -> showVersion();
                case "status" -> showStatus();
                default -> "未知命令: " + command + "\n输入 help 查看可用命令";
            };
        } catch (Exception e) {
            return "命令执行出错: " + e.getMessage();
        }
    }

    private String showHelp() {
        return """
        可用命令:
          help    - 显示此帮助信息
          version - 显示程序版本
          status  - 显示程序运行状态
          weather <城市名> - 查询指定城市天气
          exit    - 退出程序""";
    }

    private String showVersion() {
        return "Claw 助手 " + VERSION;
    }

    private String showStatus() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        return String.format("""
        程序状态:
          运行状态: 正常运行
          已用内存: %dMB / %dMB
          可用处理器: %d 核""",
                usedMemory, totalMemory, runtime.availableProcessors());
    }
}