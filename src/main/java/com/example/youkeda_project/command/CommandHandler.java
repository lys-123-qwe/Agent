package com.example.youkeda_project.command;

import com.example.youkeda_project.service.IlinkService;
import org.springframework.stereotype.Component;

@Component
public class CommandHandler {

    private static final String VERSION = "v1.0.0";

    private final IlinkService ilinkService;

    public CommandHandler(IlinkService ilinkService) {
        this.ilinkService = ilinkService;
    }

    public String handle(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "请输入命令，输入 help 查看帮助";
        }

        String command = input.trim().toLowerCase();

        try {
            if (command.startsWith("ilink ")) {
                return handleIlink(command.substring(6).trim());
            }

            return switch (command) {
                case "help" -> showHelp();
                case "version" -> showVersion();
                case "status" -> showStatus();
                case "ilink" -> showIlinkHelp();
                default -> "未知命令: " + command + "\n输入 help 查看可用命令";
            };
        } catch (Exception e) {
            return "命令执行出错: " + e.getMessage();
        }
    }

    private String handleIlink(String subCommand) {
        if ("login".equals(subCommand) || "start".equals(subCommand)) {
            return ilinkService.start();
        } else if ("stop".equals(subCommand)) {
            return ilinkService.stop();
        } else if ("status".equals(subCommand)) {
            boolean connected = ilinkService.isConnected();
            return "iLink 状态: " + (connected ? "已连接" : "未连接");
        } else {
            return "未知 iLink 命令: " + subCommand + "\n输入 ilink 查看帮助";
        }
    }

    private String showHelp() {
        return """
        可用命令:
          help    - 显示此帮助信息
          version - 显示程序版本
          status  - 显示程序运行状态
          weather <城市名> - 查询指定城市天气
          ilink   - iLink 微信 Bot 相关命令
          exit    - 退出程序""";
    }

    private String showIlinkHelp() {
        return """
        iLink 命令:
          ilink login - 启动 iLink 登录（扫码绑定微信）
          ilink status - 查看 iLink 连接状态
          ilink stop  - 断开 iLink 连接""";
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
          可用处理器: %d 核
          iLink: %s""",
                usedMemory, totalMemory, runtime.availableProcessors(),
                ilinkService.isConnected() ? "已连接" : "未连接");
    }
}