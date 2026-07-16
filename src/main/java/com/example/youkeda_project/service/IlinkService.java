package com.example.youkeda_project.service;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class IlinkService {

    private static final Logger log = LoggerFactory.getLogger(IlinkService.class);
    private static final long POLL_INTERVAL_MS = 3000;

    private ILinkClient client;
    private LoginContext loginContext;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private Thread pollThread;

    public String start() {
        if (connected.get()) {
            return "iLink 已经连接";
        }

        try {
            ILinkConfig config = ILinkConfig.builder()
                    .connectTimeoutMs(35000)
                    .readTimeoutMs(35000)
                    .heartbeatEnabled(true)
                    .build();

            client = ILinkClient.builder()
                    .config(config)
                    .onLogin(new OnLoginListener() {
                        @Override
                        public void onLoginSuccess(LoginContext context) {
                            loginContext = context;
                            connected.set(true);
                            log.info("iLink登录成功, botId={}", context.getBotId());
                            System.out.println("\n[系统] iLink 登录成功! BotID=" + context.getBotId());
                            startPolling();
                        }

                        @Override
                        public void onLoginFailure(Throwable throwable) {
                            log.error("iLink登录失败: {}", throwable.getMessage());
                            System.out.println("\n[系统] iLink 登录失败: " + throwable.getMessage());
                        }
                    })
                    .onMessage(new OnMessageListener() {
                        @Override
                        public void onMessages(List<WeixinMessage> messages) {
                            for (WeixinMessage msg : messages) {
                                String fromUser = msg.getFrom_user_id();
                                String text = extractText(msg);
                                if (text != null) {
                                    log.info("收到微信消息 from={}, text={}", fromUser, text);
                                    System.out.println("\n[微信] " + fromUser + ": " + text);
                                }
                            }
                        }
                    })
                    .build();

            String qrCode = client.executeLogin();
            return "请用微信扫码登录:\n" + qrCode;

        } catch (Exception e) {
            log.error("iLink启动失败: {}", e.getMessage(), e);
            return "错误: iLink启动失败 - " + e.getMessage();
        }
    }

    private void startPolling() {
        polling.set(true);
        pollThread = new Thread(() -> {
            log.info("iLink消息轮询已启动");
            while (polling.get() && connected.get()) {
                try {
                    List<WeixinMessage> messages = client.getUpdates();
                    if (!messages.isEmpty()) {
                        log.info("轮询到 {} 条消息", messages.size());
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("轮询消息异常: {}", e.getMessage());
                    try {
                        Thread.sleep(POLL_INTERVAL_MS * 2);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("iLink消息轮询已停止");
        }, "ilink-poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public String sendMessage(String userId, String text) {
        if (!connected.get() || client == null) {
            return "错误: iLink 未连接，请先执行 ilink login";
        }
        try {
            client.sendText(userId, text);
            log.info("已发送消息到 {}: {}", userId, text);
            return "已发送";
        } catch (Exception e) {
            log.error("发送消息失败: {}", e.getMessage());
            return "错误: 发送失败 - " + e.getMessage();
        }
    }

    public String stop() {
        polling.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        connected.set(false);
        loginContext = null;
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭iLink客户端异常: {}", e.getMessage());
            }
            client = null;
        }
        return "iLink 已断开";
    }

    public boolean isConnected() {
        return connected.get();
    }

    private String extractText(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getText_item() != null) {
                return item.getText_item().getText();
            }
        }
        return null;
    }
}