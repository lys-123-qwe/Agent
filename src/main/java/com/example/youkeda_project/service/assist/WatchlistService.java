package com.example.youkeda_project.service.assist;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
/** 自选股管理（SQLite 持久化）。 */


@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final String dbPath;

    public WatchlistService() {
        this.dbPath = "data/chat.db";
    }

    public record WatchItem(String code, String name, String addedAt) {}

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @PostConstruct
    public void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS watchlist (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     TEXT NOT NULL,
                stock_code  TEXT NOT NULL,
                stock_name  TEXT NOT NULL,
                added_at    TEXT DEFAULT (datetime('now','localtime')),
                UNIQUE(user_id, stock_code)
            )
        """;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            // 清理旧版本错误数据（存了 sh/sz 而非 sh600519/sz000858）
            int cleaned = stmt.executeUpdate("DELETE FROM watchlist WHERE length(stock_code) < 5");
            if (cleaned > 0) log.info("清理了 {} 条旧版错误自选数据", cleaned);
            log.info("自选股表初始化完成");
        } catch (Exception e) {
            log.warn("自选股表初始化失败: {}", e.getMessage());
        }
    }

    public boolean add(String userId, String code, String name) {
        String sql = "INSERT OR IGNORE INTO watchlist(user_id, stock_code, stock_name) VALUES(?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, code);
            ps.setString(3, name);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("添加自选失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean remove(String userId, String code) {
        String sql = "DELETE FROM watchlist WHERE user_id=? AND stock_code=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, code);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("删除自选失败: {}", e.getMessage());
            return false;
        }
    }

    public List<WatchItem> list(String userId) {
        String sql = "SELECT stock_code, stock_name, added_at FROM watchlist WHERE user_id=? ORDER BY added_at DESC";
        List<WatchItem> items = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new WatchItem(rs.getString("stock_code"), rs.getString("stock_name"), rs.getString("added_at")));
            }
        } catch (Exception e) {
            log.warn("查询自选失败: {}", e.getMessage());
        }
        return items;
    }
}
