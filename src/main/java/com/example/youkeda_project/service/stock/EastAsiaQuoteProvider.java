package com.example.youkeda_project.service.stock;

/**
 * 东亚市场（日股/韩股）行情数据提供者。
 * <p>
 * 当前由 {@link FakeEastAsiaQuoteProvider} 提供假数据；
 * 后续接入真实接口时，新建实现类替换即可，无需改动调用方。
 */
public interface EastAsiaQuoteProvider {

    /** 查询行情，code 为 jp_xxx（日股）或 kr_xxx（韩股），返回格式化行情文本。 */
    String queryQuote(String code);

    /** 该提供者是否支持此代码（jp_/kr_ 前缀）。 */
    boolean supports(String code);
}
