package com.example.youkeda_project;

import com.example.youkeda_project.service.NewsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NewsSearchTest {

    @Autowired
    private NewsService newsService;

    @Test
    void testNewsSearch() {
        String result = newsService.searchNews(8, "今天有什么新闻");
        System.out.println("========== 搜索结果 ==========");
        System.out.println(result);
        System.out.println("===============================");
        assertNotNull(result);
        assertFalse(result.startsWith("错误:"), "搜索失败: " + result);
    }

    @Test
    void testOfficialNews() {
        String result = newsService.searchOfficialNews();
        System.out.println("========== 今日新闻 ==========");
        System.out.println(result);
        System.out.println("===============================");
        assertNotNull(result);
        assertFalse(result.startsWith("错误:"), "搜索失败: " + result);
    }
}
