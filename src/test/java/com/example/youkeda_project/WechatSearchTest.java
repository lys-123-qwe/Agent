package com.example.youkeda_project;

import com.example.youkeda_project.service.WechatSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WechatSearchTest {

    @Autowired
    private WechatSearchService wechatSearchService;

    @Test
    void testWechatSearch() {
        String result = wechatSearchService.search("人工智能", 5);
        System.out.println("========== 微信搜索测试 ==========");
        System.out.println(result);
        System.out.println("===============================");
        assertNotNull(result);
        assertFalse(result.startsWith("错误:"), "搜索失败: " + result);
    }
}