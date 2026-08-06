package com.example.youkeda_project.skill.common;

import com.example.youkeda_project.skill.AbstractSkill;
import com.example.youkeda_project.tool.Tool;
import com.example.youkeda_project.tool.common.MemoryTool;
import com.example.youkeda_project.tool.common.ScheduledTaskTool;
import com.example.youkeda_project.tool.common.SearchTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用技能：封装搜索、记忆、定时任务等通用工具。
 */

@Component
public class CommonSkill extends AbstractSkill {

    public CommonSkill(SearchTool searchTool, MemoryTool memoryTool, ScheduledTaskTool scheduledTaskTool) {
        super(
            "common",
            "通用能力:资讯搜索,用户记忆保存,定时任务管理等基础工具.",
            List.of(
                "搜一下今天的新闻",
                "搜索AI最新进展",
                "记住我喜欢喝咖啡",
                "帮我定个提醒",
                "设置一个定时任务"
            ),
            List.of(searchTool, memoryTool, scheduledTaskTool),
            List.of()
        );
    }
}
