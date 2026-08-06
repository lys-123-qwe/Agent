package com.example.youkeda_project.skill.media;

import com.example.youkeda_project.skill.AbstractSkill;
import com.example.youkeda_project.tool.Tool;
import com.example.youkeda_project.tool.media.ImageTool;
import com.example.youkeda_project.tool.media.TtsTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 媒体技能：封装文生图与语音合成工具。
 */

@Component
public class MediaSkill extends AbstractSkill {

    public MediaSkill(ImageTool imageTool, TtsTool ttsTool) {
        super(
            "media",
            "媒体生成:文生图(画图/生成图片),语音合成(朗读/语音)等多媒体生成能力.",
            List.of(
                "画一张图",
                "生成一张猫的图片",
                "帮我朗读这段话",
                "把这段话转成语音"
            ),
            List.of(imageTool, ttsTool),
            List.of()
        );
    }
}
