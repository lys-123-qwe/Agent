package com.example.youkeda_project.controller;

import com.example.youkeda_project.cli.CliRunner;
import com.example.youkeda_project.service.wechat.WeChatSession;
import com.example.youkeda_project.service.wechat.WeChatSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Web 端微信账户管理接口：
 * 支持扫码登录、轮询登录状态、账户列表、删除账户。
 * 供前端登录页 / 账户管理页使用。
 */

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final WeChatSessionManager sessionManager;
    private final CliRunner cliRunner;

    public AuthController(WeChatSessionManager sessionManager, CliRunner cliRunner) {
        this.sessionManager = sessionManager;
        this.cliRunner = cliRunner;
    }

    /** 发起扫码登录：创建一个新会话，返回二维码图片内容 + sessionId */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam(defaultValue = "web") String label) {
        WeChatSessionManager.LoginResponse resp = sessionManager.startLogin(label);
        log.info("Web 发起登录: sessionId={}, label={}", resp.sessionId(), resp.label());
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("sessionId", resp.sessionId());
        m.put("label", resp.label());
        m.put("qrContent", resp.qrContent());
        return m;
    }

    /** 轮询扫码状态：登录成功后自动启动该会话的消息监听 */
    @GetMapping("/login/status")
    public Map<String, Object> status(@RequestParam String sessionId) {
        WeChatSession s = sessionManager.getSession(sessionId);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (s == null) {
            m.put("loggedIn", false);
            m.put("status", "NOT_FOUND");
            return m;
        }
        boolean loggedIn = s.isLoggedIn();
        if (loggedIn && !s.isListening()) {
            log.info("Web 登录成功，启动消息监听: sessionId={}", sessionId);
            try {
                cliRunner.startMessageListener(sessionId);
            } catch (Exception e) {
                log.error("启动监听失败: sessionId={}", sessionId, e);
            }
        }
        m.put("loggedIn", loggedIn);
        m.put("status", s.getStatus().name());
        m.put("botId", s.getBotId() != null ? s.getBotId() : "");
        return m;
    }

    /** 账户列表 */
    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions() {
        return sessionManager.listSessionInfos().stream().map(i -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("sessionId", i.sessionId());
            m.put("shortId", i.shortId());
            m.put("label", i.label());
            m.put("status", i.status());
            m.put("botId", i.botId() != null ? i.botId() : "");
            m.put("listening", i.listening());
            return m;
        }).toList();
    }

    /** 删除账户 */
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> delete(@PathVariable String sessionId) {
        log.info("Web 删除账户: sessionId={}", sessionId);
        try {
            sessionManager.deleteSession(sessionId);
        } catch (Exception e) {
            // 尽力删除：内存移除 + 持久化清理各自有兜底，即使异常也不阻断
            log.error("删除会话异常(尽力删除): sessionId={}", sessionId, e);
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }
}
