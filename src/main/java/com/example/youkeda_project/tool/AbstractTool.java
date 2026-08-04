package com.example.youkeda_project.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具抽象基类：提供通用 Logger 与参数解析 helper，减少各工具类的样板代码。
 */
public abstract class AbstractTool implements Tool {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 无参数工具的默认实现；有参数的工具覆写它。 */
    @Override
    public Map<String, Object> getParameters() {
        return Map.of("type", "object", "properties", new LinkedHashMap<String, Object>());
    }

    /** 取字符串参数（null 或缺失返回 null） */
    protected static String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v instanceof String s ? s : (v != null ? String.valueOf(v) : null);
    }

    /** 取整数参数（Number 或数字字符串，无法解析返回 null） */
    protected static Integer intVal(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 取小数参数（Number 或数字字符串，无法解析返回 null） */
    protected static Double dbl(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** 取布尔参数 */
    protected static Boolean bool(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return "true".equalsIgnoreCase(s) || "1".equals(s);
        return null;
    }
}
