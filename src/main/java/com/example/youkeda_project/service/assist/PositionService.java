package com.example.youkeda_project.service.assist;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
/** 持仓管理（SQLite 持久化）。 */


@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final String dbPath;

    public PositionService() {
        this.dbPath = "data/chat.db";
    }

    public record Position(String code, String name, double quantity, double buyPrice, String addedAt) {}

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @PostConstruct
    public void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS positions (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     TEXT NOT NULL,
                stock_code  TEXT NOT NULL,
                stock_name  TEXT NOT NULL,
                quantity    REAL NOT NULL,
                buy_price   REAL NOT NULL,
                added_at    TEXT DEFAULT (datetime('now','localtime')),
                UNIQUE(user_id, stock_code)
            )
        """;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            log.info("持仓表初始化完成");
        } catch (Exception e) {
            log.warn("持仓表初始化失败: {}", e.getMessage());
        }
    }

    public boolean addOrUpdate(String userId, String code, String name, double quantity, double buyPrice) {
        // 成本价按持仓量加权平均：新成本 = (旧成本*旧数量 + 新价*新数量) / (旧数量+新数量)
        String sql = "INSERT INTO positions(user_id, stock_code, stock_name, quantity, buy_price) VALUES(?,?,?,?,?) "
            + "ON CONFLICT(user_id, stock_code) DO UPDATE SET "
            + "quantity=quantity+?, "
            + "buy_price=(buy_price*quantity + ?*?) / (quantity+?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.setDouble(4, quantity);
            ps.setDouble(5, buyPrice);
            ps.setDouble(6, quantity);   // quantity=quantity+?
            ps.setDouble(7, quantity);   // 加权：新增数量
            ps.setDouble(8, buyPrice);   // 加权：新增价格
            ps.setDouble(9, quantity);   // 加权：分母（旧+新）
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("添加持仓失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean reduce(String userId, String code, double quantity) {
        String sql = "UPDATE positions SET quantity=quantity-? WHERE user_id=? AND stock_code=? AND quantity>=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, quantity);
            ps.setString(2, userId);
            ps.setString(3, code);
            ps.setDouble(4, quantity);
            if (ps.executeUpdate() > 0) {
                removeZero(userId);
                return true;
            }
        } catch (Exception e) {
            log.warn("减少持仓失败: {}", e.getMessage());
        }
        return false;
    }

    private void removeZero(String userId) {
        String sql = "DELETE FROM positions WHERE user_id=? AND quantity<=0";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("清理零持仓失败: {}", e.getMessage());
        }
    }

    public List<Position> list(String userId) {
        String sql = "SELECT stock_code, stock_name, quantity, buy_price, added_at FROM positions WHERE user_id=? ORDER BY added_at DESC";
        List<Position> items = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new Position(rs.getString("stock_code"), rs.getString("stock_name"),
                    rs.getDouble("quantity"), rs.getDouble("buy_price"), rs.getString("added_at")));
            }
        } catch (Exception e) {
            log.warn("查询持仓失败: {}", e.getMessage());
        }
        return items;
    }
}
