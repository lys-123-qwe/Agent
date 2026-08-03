package com.example.youkeda_project.ai.tool;

import com.example.youkeda_project.ai.Tool;
import com.example.youkeda_project.ai.ToolResult;
import com.example.youkeda_project.service.assist.ScheduledTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScheduledTaskTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskTool.class);

    private final ScheduledTaskService taskService;

    public ScheduledTaskTool(ScheduledTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "scheduled_task";
    }

    @Override
    public String getDescription() {
        return "定时任务管理工具.支持三种操作:\n"
            + "1. create: 创建定时任务(一次性或周期性)\n"
            + "2. cancel: 取消任务(通过任务ID或关键词)\n"
            + "3. list: 查看用户的所有定时任务";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("type", "string");
        operation.put("description", "操作类型: create / cancel / list");
        operation.put("enum", List.of("create", "cancel", "list"));
        properties.put("operation", operation);

        Map<String, Object> taskType = new LinkedHashMap<>();
        taskType.put("type", "string");
        taskType.put("description", "任务类型: once(一次性)/ periodic(周期性),仅在 create 时需要");
        taskType.put("enum", List.of("once", "periodic"));
        properties.put("task_type", taskType);

        Map<String, Object> duration = new LinkedHashMap<>();
        duration.put("type", "integer");
        duration.put("description", "时长数值,如 1,仅在一次性任务 create 时需要");
        properties.put("duration", duration);

        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("type", "string");
        unit.put("description", "时长单位: second / minute / hour,仅在一次性任务 create 时需要");
        unit.put("enum", List.of("second", "minute", "hour"));
        properties.put("unit", unit);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "string");
        message.put("description", "提醒内容/消息,如'喝水','查杭州天气',create 时需要");
        properties.put("message", message);

        Map<String, Object> time = new LinkedHashMap<>();
        time.put("type", "string");
        time.put("description", "触发时间(仅周期性任务),格式 HH:mm,如 09:00,00:00,create periodic 时需要");
        properties.put("time", time);

        Map<String, Object> dayOfWeek = new LinkedHashMap<>();
        dayOfWeek.put("type", "integer");
        dayOfWeek.put("description", "星期几(仅按周周期性任务),1=周一 到 7=周日.如不传则按日重复");
        properties.put("day_of_week", dayOfWeek);

        Map<String, Object> taskId = new LinkedHashMap<>();
        taskId.put("type", "integer");
        taskId.put("description", "任务ID,仅在 cancel 时按ID取消时需要");
        properties.put("task_id", taskId);

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "关键词,仅在 cancel 时按关键词模糊匹配取消时需要(如'喝水'会取消所有包含'喝水'的待执行任务)");
        properties.put("keyword", keyword);

        params.put("properties", properties);
        params.put("required", List.of("operation"));
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null || arguments.get("operation") == null) {
            return ToolResult.text("请指定操作类型: create / cancel / list");
        }

        String operation = (String) arguments.get("operation");

        return switch (operation) {
            case "create" -> createTask(arguments);
            case "cancel" -> cancelTask(arguments);
            case "list" -> listTasks(arguments);
            default -> ToolResult.text("不支持的操作: " + operation + ",支持: create / cancel / list");
        };
    }

    private ToolResult createTask(Map<String, Object> args) {
        String taskType = (String) args.get("task_type");
        String message = (String) args.get("message");

        if (taskType == null || message == null) {
            return ToolResult.text("创建任务缺少参数,需要 task_type(once/periodic)和 message(提醒内容)");
        }

        String userId = resolveUserId(args);
        if (userId == null) return ToolResult.text("错误: 无法识别用户");

        String triggerAt;
        String periodConfig = null;

        if ("once".equals(taskType)) {
            Number durationNum = (Number) args.get("duration");
            String unit = (String) args.get("unit");
            if (durationNum == null || unit == null) {
                return ToolResult.text("一次性任务需要 duration(时长)和 unit(second/minute/hour)");
            }
            triggerAt = ScheduledTaskService.calcTriggerOnce(durationNum.longValue(), unit);
        } else if ("periodic".equals(taskType)) {
            String time = (String) args.get("time");
            if (time == null) {
                return ToolResult.text("周期性任务需要 time(格式 HH:mm,如 09:00)");
            }
            Number dayOfWeek = (Number) args.get("day_of_week");
            triggerAt = ScheduledTaskService.calcTriggerPeriodic(time, dayOfWeek != null ? dayOfWeek.intValue() : null);
            periodConfig = dayOfWeek != null
                ? "weekly " + dayOfWeek.intValue() + " " + time
                : "daily " + time;
        } else {
            return ToolResult.text("task_type 必须是 once(一次性)或 periodic(周期性)");
        }

        if (triggerAt == null) {
            return ToolResult.text("计算触发时间失败,请检查参数");
        }

        int taskId = taskService.createTask(userId, taskType, message, triggerAt, periodConfig);
        if (taskId > 0) {
            String typeName = "once".equals(taskType) ? "一次性" : "周期性";
            return ToolResult.text("已创建" + typeName + "定时任务(ID=" + taskId + "),将在 " + triggerAt + " 触发提醒:" + message);
        }
        return ToolResult.text("创建任务失败,请稍后重试");
    }

    private ToolResult cancelTask(Map<String, Object> args) {
        String userId = resolveUserId(args);
        if (userId == null) return ToolResult.text("错误: 无法识别用户");

        Number taskIdNum = (Number) args.get("task_id");
        if (taskIdNum != null) {
            boolean ok = taskService.cancelTask(taskIdNum.intValue(), userId);
            return ok
                ? ToolResult.text("已取消任务(ID=" + taskIdNum.intValue() + ")")
                : ToolResult.text("未找到该任务或已取消,任务ID=" + taskIdNum.intValue());
        }

        String keyword = (String) args.get("keyword");
        if (keyword != null && !keyword.isBlank()) {
            int count = taskService.cancelTaskByKeyword(userId, keyword);
            if (count > 0) {
                return ToolResult.text("已取消 " + count + " 个包含\"" + keyword + "\"的定时任务");
            }
            return ToolResult.text("未找到包含\"" + keyword + "\"的待执行任务");
        }

        return ToolResult.text("请提供 task_id(任务ID)或 keyword(关键词)来取消任务");
    }

    private ToolResult listTasks(Map<String, Object> args) {
        String userId = resolveUserId(args);
        if (userId == null) return ToolResult.text("错误: 无法识别用户");

        List<Map<String, Object>> tasks = taskService.listTasks(userId);
        if (tasks.isEmpty()) {
            return ToolResult.text("你目前没有定时任务");
        }

        StringBuilder sb = new StringBuilder("你的定时任务:\n");
        for (Map<String, Object> t : tasks) {
            sb.append("- [ID=").append(t.get("id")).append("] ");
            sb.append(t.get("message")).append(" → ");
            sb.append(t.get("trigger_at"));
            String type = "once".equals(t.get("task_type")) ? "一次性" : "周期性";
            sb.append(" (").append(type).append(", ").append(t.get("status")).append(")");
            String config = (String) t.get("period_config");
            if (config != null) {
                sb.append(" [").append(config).append("]");
            }
            sb.append("\n");
        }
        return ToolResult.text(sb.toString());
    }

    private String resolveUserId(Map<String, Object> args) {
        Object uid = args.get("_user_id");
        return uid instanceof String s ? s : null;
    }
}
