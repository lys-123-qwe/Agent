package com.example.youkeda_project.service;

import com.example.youkeda_project.config.UserContextStore;
import com.example.youkeda_project.client.IlinkProtocolClient;
import com.example.youkeda_project.protocol.MessageItem;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);
    private static final String DB_URL = "jdbc:sqlite:tasks.db";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IlinkProtocolClient client;
    private final UserContextStore contextStore;
    private ScheduledExecutorService scheduler;

    public ScheduledTaskService(IlinkProtocolClient client, UserContextStore contextStore) {
        this.client = client;
        this.contextStore = contextStore;
    }

    @PostConstruct
    public void init() {
        createTableIfNotExists();
        startScheduler();
    }

    private Connection getConn() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void createTableIfNotExists() {
        try (Connection conn = getConn(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS scheduled_tasks (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     TEXT NOT NULL,
                    task_type   TEXT NOT NULL,
                    message     TEXT NOT NULL,
                    trigger_at  TEXT NOT NULL,
                    period_config TEXT,
                    status      TEXT DEFAULT 'pending',
                    created_at  TEXT DEFAULT (datetime('now','localtime'))
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_due ON scheduled_tasks(status, trigger_at)");
            log.info("定时任务表初始化完成: tasks.db");
        } catch (Exception e) {
            log.error("初始化定时任务表失败", e);
        }
    }

    public int createTask(String userId, String taskType, String message, String triggerAt, String periodConfig) {
        String sql = "INSERT INTO scheduled_tasks (user_id, task_type, message, trigger_at, period_config) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, userId);
            ps.setString(2, taskType);
            ps.setString(3, message);
            ps.setString(4, triggerAt);
            ps.setString(5, periodConfig);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.warn("创建定时任务失败", e);
        }
        return -1;
    }

    public boolean cancelTask(int taskId, String userId) {
        String sql = "UPDATE scheduled_tasks SET status = 'cancelled' WHERE id = ? AND user_id = ? AND status = 'pending'";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("取消任务失败", e);
            return false;
        }
    }

    public int cancelTaskByKeyword(String userId, String keyword) {
        String sql = "UPDATE scheduled_tasks SET status = 'cancelled' WHERE user_id = ? AND status = 'pending' AND message LIKE ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, "%" + keyword + "%");
            return ps.executeUpdate();
        } catch (Exception e) {
            log.warn("按关键词取消任务失败", e);
            return 0;
        }
    }

    public List<Map<String, Object>> listTasks(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, task_type, message, trigger_at, period_config, status FROM scheduled_tasks WHERE user_id = ? ORDER BY id DESC LIMIT 20";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("task_type", rs.getString("task_type"));
                    map.put("message", rs.getString("message"));
                    map.put("trigger_at", rs.getString("trigger_at"));
                    map.put("period_config", rs.getString("period_config"));
                    map.put("status", rs.getString("status"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            log.warn("查询任务列表失败", e);
        }
        return list;
    }

    // ============ 触发时间计算 ============

    public static String calcTriggerOnce(long duration, String unit) {
        long seconds = switch (unit) {
            case "second" -> duration;
            case "minute" -> duration * 60;
            case "hour" -> duration * 3600;
            default -> duration * 60;
        };
        return LocalDateTime.now().plusSeconds(seconds).format(DTF);
    }

    public static String calcTriggerPeriodic(String timeStr, Integer dayOfWeek) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = LocalTime.parse(timeStr);
        if (dayOfWeek != null) {
            DayOfWeek target = DayOfWeek.of(dayOfWeek);
            LocalDate nextDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(target));
            LocalDateTime next = LocalDateTime.of(nextDate, time);
            if (!next.isAfter(now)) {
                next = LocalDateTime.of(nextDate.with(TemporalAdjusters.next(target)), time);
            }
            return next.format(DTF);
        } else {
            LocalDateTime next = LocalDateTime.of(LocalDate.now(), time);
            if (!next.isAfter(now)) {
                next = next.plusDays(1);
            }
            return next.format(DTF);
        }
    }

    // ============ 后台调度 ============

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "task-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::fireDueTasks, 0, 1, TimeUnit.SECONDS);
        log.info("定时任务调度器已启动（每秒扫描）");
    }

    private void fireDueTasks() {
        String now = LocalDateTime.now().format(DTF);
        String sql = "SELECT * FROM scheduled_tasks WHERE status = 'pending' AND trigger_at <= ?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String userId = rs.getString("user_id");
                    String message = rs.getString("message");
                    String taskType = rs.getString("task_type");
                    String periodConfig = rs.getString("period_config");

                    String token = contextStore.getToken(userId);
                    if (token == null) {
                        log.warn("用户 {} 无 contextToken，无法发送提醒", userId);
                        continue;
                    }

                    try {
                        client.sendMessage(userId, token, List.of(MessageItem.text("⏰ 你设置的提醒：" + message)));
                        log.info("已发送定时提醒 userId={}, taskId={}", userId, id);
                    } catch (Exception e) {
                        log.warn("发送定时提醒失败 userId={}, taskId={}", userId, id, e);
                        continue;
                    }

                    if ("once".equals(taskType)) {
                        updateTaskStatus(conn, id, "done");
                    } else if ("periodic".equals(taskType) && periodConfig != null) {
                        String next = calcNextTrigger(periodConfig);
                        if (next != null) {
                            try (PreparedStatement up = conn.prepareStatement("UPDATE scheduled_tasks SET trigger_at = ? WHERE id = ?")) {
                                up.setString(1, next);
                                up.setInt(2, id);
                                up.executeUpdate();
                            }
                        } else {
                            updateTaskStatus(conn, id, "done");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("定时任务扫描失败", e);
        }
    }

    private void updateTaskStatus(Connection conn, int id, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE scheduled_tasks SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private String calcNextTrigger(String periodConfig) {
        try {
            String[] parts = periodConfig.split(" ");
            if ("daily".equals(parts[0]) && parts.length >= 2) {
                LocalTime time = LocalTime.parse(parts[1]);
                LocalDateTime next = LocalDateTime.of(LocalDate.now(), time);
                if (!next.isAfter(LocalDateTime.now())) {
                    next = next.plusDays(1);
                }
                return next.format(DTF);
            } else if ("weekly".equals(parts[0]) && parts.length >= 3) {
                int dayOfWeek = Integer.parseInt(parts[1]);
                LocalTime time = LocalTime.parse(parts[2]);
                LocalDate nextDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
                LocalDateTime next = LocalDateTime.of(nextDate, time);
                if (!next.isAfter(LocalDateTime.now())) {
                    next = LocalDateTime.of(nextDate.with(TemporalAdjusters.next(DayOfWeek.of(dayOfWeek))), time);
                }
                return next.format(DTF);
            }
        } catch (Exception e) {
            log.warn("计算下次触发时间失败 period_config={}", periodConfig, e);
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("定时任务调度器已关闭");
        }
    }
}
