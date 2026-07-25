package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.FortuneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IChingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(IChingTool.class);
    private final FortuneService fortuneService;

    public IChingTool(FortuneService fortuneService) {
        this.fortuneService = fortuneService;
    }

    @Override
    public String getName() { return "iching_divination"; }

    @Override
    public String getDescription() {
        return "【六爻占卜】输入想问的具体事情（寻物/出行/考试/事业/感情等），AI起卦解卦，分析吉凶和发展趋势。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "string");
        question.put("description", "占卜的具体问题，如：我的钥匙丢在哪了、下周考试能通过吗、最近适合换工作吗");
        p.put("question", question);
        params.put("properties", p);
        params.put("required", new String[]{"question"});
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (args == null) return ToolResult.text("请告诉我你想占卜什么问题");
        String question = args.get("question") instanceof String s ? s : null;
        if (question == null || question.isBlank()) return ToolResult.text("请告诉我你想占卜什么问题");
        log.info("IChingTool: {}", question);
        return ToolResult.text(fortuneService.iching(question));
    }
}
