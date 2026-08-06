package com.example.youkeda_project.skill.life;

import com.example.youkeda_project.skill.AbstractSkill;
import com.example.youkeda_project.tool.Tool;
import com.example.youkeda_project.tool.life.ConstellationTool;
import com.example.youkeda_project.tool.life.CopywritingTool;
import com.example.youkeda_project.tool.life.DateTimeTool;
import com.example.youkeda_project.tool.life.FortuneTool;
import com.example.youkeda_project.tool.life.HotCommentTool;
import com.example.youkeda_project.tool.life.LogisticsTool;
import com.example.youkeda_project.tool.life.MusicTool;
import com.example.youkeda_project.tool.life.PhoneLocationTool;
import com.example.youkeda_project.tool.life.TranslationTool;
import com.example.youkeda_project.tool.life.TravelTool;
import com.example.youkeda_project.tool.life.WeatherTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 生活技能：封装生活/娱乐工具（天气/翻译/出行/物流/音乐/运势/文案/快递/星座等）。
 */

@Component
public class LifeSkill extends AbstractSkill {

    public LifeSkill(
            WeatherTool weatherTool,
            TranslationTool translationTool,
            TravelTool travelTool,
            LogisticsTool logisticsTool,
            PhoneLocationTool phoneLocationTool,
            MusicTool musicTool,
            HotCommentTool hotCommentTool,
            ConstellationTool constellationTool,
            FortuneTool fortuneTool,
            CopywritingTool copywritingTool,
            DateTimeTool dateTimeTool) {
        super(
            "life-assistant",
            "生活服务:天气查询,翻译/方言,出行路线,快递物流,手机号归属地,音乐与热评,星座运势,八字命理,文案生成,日期计算等日常生活服务.",
            List.of(
                "查北京天气",
                "翻译这句话",
                "从天河客运站到广州塔怎么走",
                "查快递单号",
                "查手机号归属地",
                "推荐一些歌",
                "来条网易云热评",
                "今天运势怎么样",
                "帮我写个朋友圈文案",
                "今天农历几号",
                "帮我算八字"
            ),
            List.of(weatherTool, translationTool, travelTool, logisticsTool,
                    phoneLocationTool, musicTool, hotCommentTool,
                    constellationTool, fortuneTool, copywritingTool, dateTimeTool),
            List.of()
        );
    }
}
