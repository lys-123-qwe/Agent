package com.example.youkeda_project.service.assist;

import com.example.youkeda_project.config.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
/** 文案生成（朋友圈/小红书等）。 */


@Service
public class CopywritingService {

    private static final Logger log = LoggerFactory.getLogger(CopywritingService.class);

    private final ApiConfig apiConfig;
    private final String apiUrl;
    private final ChatTextClient chatTextClient;

    public CopywritingService(ApiConfig apiConfig, ChatTextClient chatTextClient) {
        this.apiConfig = apiConfig;
        this.apiUrl = apiConfig.getTranslationApiUrl();
        this.chatTextClient = chatTextClient;
    }

    public String generate(String requirement, String scene, String tone, String emoji) {
        String apiKey = apiConfig.getImageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置阿里云 API Key";
        }

        String systemPrompt = buildGeneratePrompt(scene, tone, emoji);

        try {
            log.info("文案生成请求: scene={}, tone={}, emoji={}, reqLen={}", scene, tone, emoji, requirement.length());

            String result = chatTextClient.complete(apiUrl, apiKey, "qwen-turbo", systemPrompt, requirement, Duration.ofSeconds(30));
            if (result == null) return "文案生成服务暂不可用";

            if (result.isBlank()) return "文案生成结果为空";
            return result;
        } catch (Exception e) {
            log.error("文案生成失败", e);
            return "文案生成失败: " + e.getMessage();
        }
    }

    public String rewrite(String oldCopy, String style, String scene, String modifyRequest) {
        String apiKey = apiConfig.getImageApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "错误: 未配置阿里云 API Key";
        }

        String systemPrompt = buildRewritePrompt(style, scene);

        try {
            String userContent = "原文案：\n" + oldCopy + "\n\n修改需求：" + modifyRequest;

            log.info("文案改写请求: style={}, scene={}, modifyReqLen={}", style, scene, modifyRequest.length());

            String result = chatTextClient.complete(apiUrl, apiKey, "qwen-turbo", systemPrompt, userContent, Duration.ofSeconds(30));
            if (result == null) return "文案改写服务暂不可用";

            if (result.isBlank()) return "文案改写结果为空";
            return result;
        } catch (Exception e) {
            log.error("文案改写失败", e);
            return "文案改写失败: " + e.getMessage();
        }
    }

    private String buildGeneratePrompt(String scene, String tone, String emoji) {
        String sceneHint = "";
        if (scene != null && !scene.isBlank()) {
            sceneHint = switch (scene) {
                case "朋友圈" -> "发布到微信朋友圈的文案，适合配图发圈，语气亲切自然，带适当emoji和话题感";
                case "抖音" -> "发布到抖音的文案，短小精悍，有爆点或互动感，适合短视频标题，可带热门话题标签";
                case "微博" -> "发布到微博的文案，适合公开发布，有一定话题性和讨论度，可带话题标签";
                case "小红书" -> "发布到小红书的文案，种草风格，真诚分享感，适合图文笔记，可带emoji和标签";
                default -> "";
            };
        }

        String toneHint = "";
        if (tone != null && !tone.isBlank()) {
            toneHint = "整体语气要求：" + tone + "风格贯穿三条文案。\n";
        }

        String emojiHint = "";
        if (emoji != null && !emoji.isBlank()) {
            emojiHint = switch (emoji) {
                case "无" -> "不使用任何emoji。";
                case "轻量" -> "适当使用1-2个emoji点缀。";
                case "丰富" -> "使用多个emoji增强表达和氛围。";
                default -> "";
            };
        }

        return """
你是一个专业文案写手。根据用户提供的主题/要求，生成3种固定风格的文案。

输出格式要求（严格按此格式，不要添加额外说明）：
━━━ 简约治愈 ━━━
[文案内容]

━━━ 文艺高级 ━━━
[文案内容]

━━━ 励志走心 ━━━
[文案内容]

━━━ 优化建议 ━━━
简约治愈：[1句改进方向]
文艺高级：[1句改进方向]
励志走心：[1句改进方向]

风格要求：
1. 简约治愈：短句为主，温暖柔软，像轻声安慰，让人感到平静和治愈
2. 文艺高级：有文学感，用词考究，意象优美，有格调但不晦涩
3. 励志走心：有力量感，真诚有力，能打动人，有共鸣不空洞

""" + toneHint + (sceneHint.isEmpty() ? "场景要求：通用风格，不指定特定平台\n" : "场景要求：" + sceneHint + "\n")
            + (emojiHint.isEmpty() ? "" : "emoji要求：" + emojiHint + "\n") + """
每条文案不超过100字。只输出文案和优化建议，不要有其他解释。""";
    }

    private String buildRewritePrompt(String style, String scene) {
        String styleHint = switch (style != null ? style : "") {
            case "简约治愈" -> "简约治愈风格：短句为主，温暖柔软，让人感到平静和治愈";
            case "文艺高级" -> "文艺高级风格：有文学感，用词考究，意象优美，有格调但不晦涩";
            case "励志走心" -> "励志走心风格：有力量感，真诚有力，能打动人，有共鸣不空洞";
            default -> "";
        };

        String sceneHint = "";
        if (scene != null && !scene.isBlank()) {
            sceneHint = switch (scene) {
                case "朋友圈" -> "，适合发布到微信朋友圈";
                case "抖音" -> "，适合发布到抖音";
                case "微博" -> "，适合发布到微博";
                case "小红书" -> "，适合发布到小红书";
                default -> "";
            };
        }

        return """
你是一个专业文案写手。用户会提供原文案和修改需求，请在保留原文案核心内容和结构的基础上，按需求修改。

规则：
1. 严格在原文案基础上修改，不要全部重写
2. 保留原文案的核心表达和金句
3. 只修改用户指定的部分
4. 修改后保持""" + styleHint + sceneHint + """
5. 直接输出修改后的文案，不要有任何解释和说明""";
    }
}
