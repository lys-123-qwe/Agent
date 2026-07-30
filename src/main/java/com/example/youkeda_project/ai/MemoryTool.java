package com.example.youkeda_project.ai;

import com.example.youkeda_project.service.ChatHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户信息记忆工具(Function Calling).
 * <p>
 * 定义 save_user_info 函数,当 AI 在对话中了解到用户的偏好,身份等信息时调用,
 * 存入 user_memory 表,下次对话时自动注入 system prompt 实现长期记忆.
 */
@Component
public class MemoryTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MemoryTool.class);

    private final ChatHistoryService chatHistoryService;

    public MemoryTool(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public String getName() {
        return "save_user_info";
    }

    @Override
    public String getDescription() {
        return "保存用户在对话中提到的重要个人信息."
            + "当用户主动说自己的昵称,喜好,职业,所在地,习惯等个人信息时,必须调用此工具记录下来."
            + "例如:用户说「叫我小明」→ key=nickname, value=小明;"
            + "用户说「我喜欢吃辣」→ key=food_preference, value=爱吃辣."
            + "不要为无关紧要的信息调用此工具.";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> key = new LinkedHashMap<>();
        key.put("type", "string");
        key.put("description", "信息类别,如 nickname(昵称),food_preference(食物偏好),location(所在地),occupation(职业),hobby(爱好),pet(宠物)等");
        properties.put("key", key);

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", "string");
        value.put("description", "具体信息内容,如「小明」「爱吃辣」「北京」「程序员」");
        properties.put("value", value);

        params.put("properties", properties);
        params.put("required", java.util.List.of("key", "value"));

        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String key = arguments != null ? (String) arguments.get("key") : null;
        String value = arguments != null ? (String) arguments.get("value") : null;

        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return ToolResult.text("错误: key 和 value 都不能为空");
        }

        // MemoryTool 需要 userId,但 Tool 接口没传 userId.
        // 这里暂时只能返回提示,实际保存需要在调用方通过 ThreadLocal 或其他方式传递 userId.
        // 暂时通过 AIService 的静态上下文传递.
        String userId = AIService.getCurrentUserId();
        if (userId == null) {
            log.warn("MemoryTool: 无法获取当前 userId,跳过保存 key={}", key);
            return ToolResult.text("错误: 无法确定当前用户");
        }

        chatHistoryService.saveMemory(userId, key.trim(), value.trim());
        log.info("MemoryTool 保存: userId={}, key={}, value={}", userId, key, value);
        return ToolResult.text("已记住: " + key + " = " + value);
    }
}
