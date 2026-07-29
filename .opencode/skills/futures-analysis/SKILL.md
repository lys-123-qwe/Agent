---
name: futures-analysis
description: 国际/国内期货实时行情查询与走势图生成（折线图/K线图）
---

## get_futures 工具

### 实时行情
```json
{"futures": "沪金"}
```

### 折线图
```json
{"futures": "螺纹钢", "days": 30}
```

### K线图
```json
{"futures": "螺纹钢", "days": 30, "chart_type": "candlestick"}
```

### 参数说明
- `futures`（必填）：名称或代码，如 沪金 / nf_AU0 / GC / hf_GC
- `days`（可选）：7=近一周，30=近一月，90=近三月。不填=仅实时行情
- `chart_type`（可选）：`line`=折线图（默认），`candlestick`=K线图

### 限制
- 国际期货（hf_ 前缀）暂不支持历史 K 线图表，建议使用国内品种
- 国内期货（nf_ 前缀）均支持走势图
