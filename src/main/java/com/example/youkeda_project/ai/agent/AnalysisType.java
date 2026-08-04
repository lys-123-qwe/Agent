package com.example.youkeda_project.ai.agent;

/**
 * 金融分析类型枚举。
 * <p>
 * 定义分析 Agent 支持的所有分析场景，
 * 每种类型对应不同的数据采集策略和 System Prompt 模板。
 */
public enum AnalysisType {

    /** 个股深度分析：技术面 + 基本面 + 机构持仓 */
    STOCK_ANALYSIS("个股分析", "分析单只股票的技术面、估值水平、近期走势和基本面"),

    /** 基金分析：业绩归因 + 持仓分析 + 经理评价 + 同类排名 */
    FUND_ANALYSIS("基金分析", "分析基金的业绩表现、持仓结构、基金经理和同类排名"),

    /** 期货趋势分析：价格趋势 + 基差 + 库存周期 */
    FUTURES_TREND("期货趋势", "分析期货合约的价格趋势、基差结构和供需基本面"),

    /** 板块产业链分析：从上游期货→中游股票→下游ETF全链路 */
    SECTOR_ANALYSIS("板块产业链", "沿产业链分析上下游：上游原料期货→中游制造股票→下游终端ETF"),

    /** 跨市场联动：期现联动 + 股债跷跷板 + 汇率传导 */
    CROSS_MARKET("跨市场联动", "分析不同市场间的联动关系：期货价格变动对股票和相关基金的影响"),

    /** ETF与主动基金对比 */
    ETF_VS_FUND("基金对比", "对比ETF和主动管理基金的费率、业绩、持仓差异"),

    /** 持仓组合诊断：用户持仓的风险收益特征 */
    PORTFOLIO_DIAGNOSIS("持仓诊断", "分析用户持仓组合（股票+基金+期货）的风险收益特征和相关性"),

    /** 资产配置建议：股/债/商品/现金比例 */
    ASSET_ALLOCATION("资产配置", "根据市场环境和风险偏好，建议大类资产配置比例"),

    /** 大盘综评：A股/港股/美股 + 国债期货 + 汇率全景 */
    MARKET_OVERVIEW("大盘综评", "A股/港股/美股主要指数、国债期货、汇率全景分析"),

    /** 资讯解读：重要新闻对各市场的传导影响 */
    NEWS_IMPACT("资讯解读", "分析最新财经新闻对股票、期货、基金市场的影响"),

    /** 通用分析（自动判断） */
    GENERAL("综合分析", "根据用户问题自动判断分析类型，综合多维度数据");

    private final String label;
    private final String description;

    AnalysisType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }

    @Override
    public String toString() { return label; }
}
