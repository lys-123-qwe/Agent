package com.example.youkeda_project.service.stock;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 东亚市场（日股/韩股）假数据源。
 * <p>
 * 暂时没有日韩股行情接口，先用 SQLite 存一批模拟数据，
 * 让查询流程能跑通；后续接入真实接口时，实现 {@link EastAsiaQuoteProvider}
 * 并替换本类即可。
 */
@Component
@ConditionalOnProperty(name = "market.eastasia.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEastAsiaQuoteProvider implements EastAsiaQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeEastAsiaQuoteProvider.class);
    private static final String DB_PATH = System.getProperty("user.home") + "/.youkeda/fake_market.db";

    /** 预置的日股/韩股模拟数据（name, code, price, changePct, open, high, low, volume, currency） */
    private static final List<Object[]> FAKE_STOCKS = List.of(
        // === 日股（日元） ===
        new Object[]{"JP", "jp_7203", "丰田汽车", 3450.0, 1.25, 3410.0, 3490.0, 3380.0, 18200000.0, "JPY"},
        new Object[]{"JP", "jp_6758", "索尼集团", 13890.0, -0.80, 14010.0, 14200.0, 13820.0, 3210000.0, "JPY"},
        new Object[]{"JP", "jp_7974", "任天堂", 8420.0, 0.45, 8380.0, 8500.0, 8310.0, 1540000.0, "JPY"},
        new Object[]{"JP", "jp_9984", "软银集团", 9850.0, 2.10, 9650.0, 9960.0, 9600.0, 11200000.0, "JPY"},
        new Object[]{"JP", "jp_6501", "日立制作所", 4205.0, -0.35, 4220.0, 4280.0, 4160.0, 8960000.0, "JPY"},
        new Object[]{"JP", "jp_7267", "本田汽车", 1540.0, 0.65, 1530.0, 1558.0, 1522.0, 24500000.0, "JPY"},
        new Object[]{"JP", "jp_8306", "三菱UFJ金融", 1685.0, 1.80, 1655.0, 1700.0, 1648.0, 43200000.0, "JPY"},
        // === 韩股（韩元） ===
        new Object[]{"KR", "kr_005930", "三星电子", 74800.0, 1.10, 74000.0, 75600.0, 73600.0, 9800000.0, "KRW"},
        new Object[]{"KR", "kr_000660", "SK海力士", 186500.0, 3.20, 181000.0, 189500.0, 180200.0, 4100000.0, "KRW"},
        new Object[]{"KR", "kr_005380", "现代汽车", 245500.0, -0.90, 247000.0, 250500.0, 243000.0, 1200000.0, "KRW"},
        new Object[]{"KR", "kr_051910", "LG化学", 382000.0, 0.50, 380000.0, 390000.0, 376000.0, 350000.0, "KRW"},
        new Object[]{"KR", "kr_000270", "起亚汽车", 112800.0, 0.75, 112000.0, 114500.0, 111000.0, 890000.0, "KRW"},
        new Object[]{"KR", "kr_035420", "NAVER", 215500.0, -1.20, 218000.0, 221000.0, 213500.0, 560000.0, "KRW"},
        new Object[]{"KR", "kr_207940", "三星生物", 905000.0, 0.30, 902000.0, 915000.0, 897000.0, 120000.0, "KRW"}
    );

    @PostConstruct
    public void init() {
        try (Connection conn = getConn(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fake_stock (
                    market    TEXT NOT NULL,
                    code      TEXT PRIMARY KEY,
                    name      TEXT NOT NULL,
                    price     REAL NOT NULL,
                    change_pct REAL NOT NULL,
                    open      REAL NOT NULL,
                    high      REAL NOT NULL,
                    low       REAL NOT NULL,
                    volume    REAL NOT NULL,
                    currency  TEXT NOT NULL
                )
            """);
        } catch (Exception e) {
            log.error("初始化日韩股假数据表失败", e);
        }
        seed();
    }

    /** 写入预置假数据（已存在则跳过） */
    private void seed() {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO fake_stock (market, code, name, price, change_pct, open, high, low, volume, currency) "
                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Object[] s : FAKE_STOCKS) {
                ps.setString(1, (String) s[0]);
                ps.setString(2, (String) s[1]);
                ps.setString(3, (String) s[2]);
                ps.setDouble(4, (Double) s[3]);
                ps.setDouble(5, (Double) s[4]);
                ps.setDouble(6, (Double) s[5]);
                ps.setDouble(7, (Double) s[6]);
                ps.setDouble(8, (Double) s[7]);
                ps.setDouble(9, (Double) s[8]);
                ps.setString(10, (String) s[9]);
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("日韩股假数据预置完成: {} 条", FAKE_STOCKS.size());
        } catch (Exception e) {
            log.error("日韩股假数据预置失败", e);
        }
    }

    @Override
    public boolean supports(String code) {
        return code != null && (code.startsWith("jp_") || code.startsWith("kr_"));
    }

    @Override
    public String queryQuote(String code) {
        if (!supports(code)) return "错误: 不支持的东亚市场代码 " + code;
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM fake_stock WHERE code = ?")) {
            ps.setString(1, code.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "错误: 未找到 " + code + " 的模拟数据（支持的：丰田、索尼、三星电子等）";
                }
                return formatQuote(rs);
            }
        } catch (Exception e) {
            log.error("查询日韩股假数据失败: code={}", code, e);
            return "错误: 查询失败 " + e.getMessage();
        }
    }

    private String formatQuote(ResultSet rs) throws Exception {
        String market = "JP".equals(rs.getString("market")) ? "日股" : "韩股";
        String name = rs.getString("name");
        String code = rs.getString("code");
        double price = rs.getDouble("price");
        double changePct = rs.getDouble("change_pct");
        String currency = rs.getString("currency");
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String sign = changePct >= 0 ? "+" : "";
        return String.format("""
            📊 %s（%s）%s行情

            | 项目 | 数据 |
            |------|------|
            | 🔢 **当前价** | **%.2f %s** |
            | 📈 **涨跌幅** | %s%.2f%% |
            | 🏷️ **今开** | %.2f %s |
            | 📊 **最高** | %.2f %s |
            | 📉 **最低** | %.2f %s |
            | 📦 **成交量** | %.0f 股 |
            | 🕐 **数据时间** | %s |

            > ⚠️ 当前为模拟数据（暂无日韩股真实接口），接入真实接口后自动替换。""",
            name, code, market, price, currency, sign, changePct,
            rs.getDouble("open"), currency, rs.getDouble("high"), currency,
            rs.getDouble("low"), currency, rs.getDouble("volume"), now);
    }

    private Connection getConn() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        conn.setAutoCommit(true);
        return conn;
    }
}
