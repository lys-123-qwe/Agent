package com.example.youkeda_project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Multi-session configuration for WeChat iLink bot management.
 * Each session is an independent bot login with its own IlinkProtocolClient,
 * thread pool, contextToken, and QR code.
 */
@Configuration
@ConfigurationProperties(prefix = "wechat.session")
public class SessionConfig {

    /** QR scan timeout in milliseconds (default 120s). */
    private long loginTimeoutMs = 120_000L;

    /** IO core threads per session (default 1 — minimize thread count). */
    private int ioCoreThreads = 1;

    /** IO max threads per session (default 2). */
    private int ioMaxThreads = 2;

    /** Max concurrent sessions. */
    private int maxSessions = 10;

    /** Directory for persisting session data. */
    private String persistDir = "./data/sessions";

    /** Auto-restore saved sessions on startup. */
    private boolean autoRestore = true;

    // ---- getters / setters ----

    public long getLoginTimeoutMs() { return loginTimeoutMs; }
    public void setLoginTimeoutMs(long v) { this.loginTimeoutMs = v; }

    public int getIoCoreThreads() { return ioCoreThreads; }
    public void setIoCoreThreads(int v) { this.ioCoreThreads = v; }

    public int getIoMaxThreads() { return ioMaxThreads; }
    public void setIoMaxThreads(int v) { this.ioMaxThreads = v; }

    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int v) { this.maxSessions = v; }

    public String getPersistDir() { return persistDir; }
    public void setPersistDir(String v) { this.persistDir = v; }

    public boolean isAutoRestore() { return autoRestore; }
    public void setAutoRestore(boolean v) { this.autoRestore = v; }
}
