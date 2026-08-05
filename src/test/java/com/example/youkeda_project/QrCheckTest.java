package com.example.youkeda_project;

import com.example.youkeda_project.service.wechat.WeChatSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class QrCheckTest {
    @Autowired
    private WeChatSessionManager sm;

    @Test
    void test() {
        try {
            var resp = sm.startLogin("qr-check");
            String q = resp.qrContent();
            System.out.println("QR_LEN=" + (q == null ? 0 : q.length()));
            System.out.println("QR_PREFIX=" + (q != null ? q.substring(0, Math.min(120, q.length())) : "null"));
            sm.deleteSession(resp.sessionId());
        } catch (Exception e) {
            System.out.println("QR_EX=" + e.getMessage());
        }
    }
}