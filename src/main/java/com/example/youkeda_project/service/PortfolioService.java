package com.example.youkeda_project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    private final DataSource dataSource;
    private final StockService stockService;

    public PortfolioService(StockService stockService) {
        this.stockService = stockService;
        this.dataSource = new SqliteDataSource();
        createTableIfNotExists();
    }

    public static class Position {
        private final String stockCode;
        private final String stockName;
        private final double shares;
        private final double costPrice;

        public Position(String stockCode, String stockName, double shares, double costPrice) {
            this.stockCode = stockCode; this.stockName = stockName;
            this.shares = shares; this.costPrice = costPrice;
        }

        public String getStockCode() { return stockCode; }
        public String getStockName() { return stockName; }
        public double getShares() { return shares; }
        public double getCostPrice() { return costPrice; }
    }

    private Connection getConn() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(true);
        return conn;
    }

    private void createTableIfNotExists() {
        try (Connection conn = getConn(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS portfolio (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    stock_code TEXT NOT NULL,
                    stock_name TEXT,
                    shares REAL NOT NULL,
                    cost_price REAL NOT NULL,
                    create_time TEXT DEFAULT (datetime('now','localtime'))
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_portfolio_user ON portfolio(user_id)");
            log.info("持仓表初始化完成");
        } catch (Exception e) {
            log.error("初始化持仓表失败", e);
        }
    }

    public void addPosition(String userId, String stockCode, String stockName, double shares, double costPrice) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO portfolio (user_id, stock_code, stock_name, shares, cost_price) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, stockCode);
            ps.setString(3, stockName);
            ps.setDouble(4, shares);
            ps.setDouble(5, costPrice);
            ps.executeUpdate();
            log.info("持仓已添加: user={}, code={}, shares={}, cost={}", userId, stockCode, shares, costPrice);
        } catch (Exception e) {
            log.warn("添加持仓失败", e);
        }
    }

    public void removePosition(String userId, String stockCode) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM portfolio WHERE user_id = ? AND stock_code = ?")) {
            ps.setString(1, userId);
            ps.setString(2, stockCode);
            ps.executeUpdate();
            log.info("持仓已清仓: user={}, code={}", userId, stockCode);
        } catch (Exception e) {
            log.warn("清仓失败", e);
        }
    }

    public List<Position> getPositions(String userId) {
        List<Position> list = new ArrayList<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT stock_code, stock_name, shares, cost_price FROM portfolio WHERE user_id = ? ORDER BY id")) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Position(
                    rs.getString("stock_code"),
                    rs.getString("stock_name"),
                    rs.getDouble("shares"),
                    rs.getDouble("cost_price")
                ));
            }
        } catch (Exception e) {
            log.warn("查询持仓失败", e);
        }
        return list;
    }

    public String queryPortfolio(String userId) {
        List<Position> positions = getPositions(userId);
        if (positions.isEmpty()) {
            return "你还没有记录任何持仓。可以告诉我记录持仓，例如：\n- 记录我买了100股茅台，成本1200\n- 买了500股宁德时代，成本250";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【我的持仓】\n\n");
        double totalCost = 0, totalMarket = 0;

        for (Position p : positions) {
            double cost = p.getShares() * p.getCostPrice();
            totalCost += cost;
            sb.append(p.getStockName()).append("（").append(p.getStockCode()).append("）\n");
            sb.append("  持有: ").append(formatNum(p.getShares())).append("股  成本: ").append(formatPrice(p.getCostPrice())).append("\n");

            String priceResult = stockService.queryStock(p.getStockCode());
            if (priceResult != null && !priceResult.startsWith("错误:")) {
                String currentPrice = extractPrice(priceResult);
                if (currentPrice != null) {
                    double cur = Double.parseDouble(currentPrice);
                    double marketValue = p.getShares() * cur;
                    totalMarket += marketValue;
                    double profit = marketValue - cost;
                    double profitPct = cost > 0 ? profit / cost * 100 : 0;
                    sb.append("  现价: ").append(formatPrice(cur));
                    sb.append("  市值: ").append(formatPrice(marketValue));
                    sb.append("  盈亏: ").append(String.format("%+.2f (%+.2f%%)", profit, profitPct)).append("\n");
                } else {
                    sb.append("  现价: 暂未获取到\n");
                }
            } else {
                sb.append("  现价: 暂未获取到\n");
            }
            sb.append("\n");
        }

        if (totalMarket > 0) {
            sb.append("总投入: ").append(formatPrice(totalCost));
            sb.append("  总市值: ").append(formatPrice(totalMarket));
            sb.append("  总盈亏: ").append(String.format("%+.2f (%+.2f%%)", totalMarket - totalCost,
                totalCost > 0 ? (totalMarket - totalCost) / totalCost * 100 : 0));
        }
        return sb.toString();
    }

    private String extractPrice(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("当前价:")) {
                return line.replaceAll(".*当前价: ([\\d.]+).*", "$1");
            }
        }
        return null;
    }

    private String formatNum(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.2f", v);
    }

    private String formatPrice(double v) {
        return String.format("%.2f", v);
    }

    private static class SqliteDataSource implements DataSource {
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(DB_URL);
        }
        public Connection getConnection(String username, String password) { return null; }
        public PrintWriter getLogWriter() { return null; }
        public void setLogWriter(PrintWriter out) {}
        public void setLoginTimeout(int seconds) {}
        public int getLoginTimeout() { return 0; }
        public java.util.logging.Logger getParentLogger() { return null; }
        public <T> T unwrap(Class<T> iface) { return null; }
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
