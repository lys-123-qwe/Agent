package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.CopywritingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CopywritingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CopywritingTool.class);

    private final CopywritingService copywritingService;

    public CopywritingTool(CopywritingService copywritingService) {
        this.copywritingService = copywritingService;
    }

    @Override
    public String getName() {
        return "copywriting";
    }

    @Override
    public String getDescription() {
        return "【文案生成/改写】根据主题要求一次性生成3种风格文案（简约治愈、文艺高级、励志走心），附带每条优化建议。"
            + "支持指定发布场景（朋友圈、抖音、微博、小红书）、语气（幽默/温柔/霸气等）、emoji数量。"
            + "传 oldCopy 为改写模式（在原文案上修改），不传 oldCopy 为生成模式。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("type", "string");
        requirement.put("description", "文案主题/要求，如'春天赏花''毕业季告别''新产品上市推广'等。生成模式时必填");
        properties.put("requirement", requirement);

        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("type", "string");
        scene.put("description", "发布场景（可选）：朋友圈、抖音、微博、小红书。不指定则通用风格");
        scene.put("enum", new String[]{"朋友圈", "抖音", "微博", "小红书"});
        properties.put("scene", scene);

        Map<String, Object> tone = new LinkedHashMap<>();
        tone.put("type", "string");
        tone.put("description", "整体语气（可选）：幽默、温柔、霸气、严肃、轻松、浪漫、简洁。不指定则根据风格自动适配");
        properties.put("tone", tone);

        Map<String, Object> emoji = new LinkedHashMap<>();
        emoji.put("type", "string");
        emoji.put("description", "emoji使用程度（可选）：无、轻量、丰富。不指定则根据场景自动适配");
        emoji.put("enum", new String[]{"无", "轻量", "丰富"});
        properties.put("emoji", emoji);

        Map<String, Object> oldCopy = new LinkedHashMap<>();
        oldCopy.put("type", "string");
        oldCopy.put("description", "原文案内容（改写模式时必填）。传此参数则为改写模式，在原文案基础上修改");
        properties.put("oldCopy", oldCopy);

        Map<String, Object> style = new LinkedHashMap<>();
        style.put("type", "string");
        style.put("description", "目标风格（改写模式时必填）：简约治愈、文艺高级、励志走心");
        style.put("enum", new String[]{"简约治愈", "文艺高级", "励志走心"});
        properties.put("style", style);

        Map<String, Object> modifyRequest = new LinkedHashMap<>();
        modifyRequest.put("type", "string");
        modifyRequest.put("description", "修改需求，如'语气再温暖一些''加一句关于春天的描述''缩短到50字以内'。改写模式时必填");
        properties.put("modifyRequest", modifyRequest);

        params.put("properties", properties);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return ToolResult.text("请提供文案主题或原文案内容");
        }

        String oldCopy = arguments.get("oldCopy") instanceof String s ? s : null;

        boolean isRewrite = oldCopy != null && !oldCopy.isBlank();

        if (isRewrite) {
            String style = arguments.get("style") instanceof String s ? s : null;
            String scene = arguments.get("scene") instanceof String s ? s : null;
            String modifyRequest = arguments.get("modifyRequest") instanceof String s ? s : null;

            if (style == null || style.isBlank()) {
                return ToolResult.text("改写模式请指定目标风格（简约治愈、文艺高级、励志走心）");
            }
            if (modifyRequest == null || modifyRequest.isBlank()) {
                return ToolResult.text("改写模式请描述修改需求");
            }

            log.info("CopywritingTool: 改写模式, style={}, scene={}", style, scene);
            String result = copywritingService.rewrite(oldCopy, style, scene, modifyRequest);
            return ToolResult.text(result);
        }

        String requirement = arguments.get("requirement") instanceof String s ? s : null;
        if (requirement == null || requirement.isBlank()) {
            return ToolResult.text("请提供文案主题或要求");
        }

        String scene = arguments.get("scene") instanceof String s ? s : null;
        String tone = arguments.get("tone") instanceof String s ? s : null;
        String emoji = arguments.get("emoji") instanceof String s ? s : null;
        log.info("CopywritingTool: 生成模式, scene={}, tone={}, emoji={}", scene, tone, emoji);
        String result = copywritingService.generate(requirement, scene, tone, emoji);
        return ToolResult.text(result);
    }
}
