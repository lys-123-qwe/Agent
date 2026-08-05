package com.example.youkeda_project.util;

import java.util.concurrent.ConcurrentHashMap;

/** 简单 TTL 缓存：按 key 存 {时间戳, 值}，超过 TTL 毫秒视为过期。 */
public class TtlCache<V> {
    private final ConcurrentHashMap<String, long[]> tsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, V> dataMap = new ConcurrentHashMap<>();
    private final long ttlMs;

    public TtlCache(long ttlMs) { this.ttlMs = ttlMs; }

    public V get(String key) {
        long[] ts = tsMap.get(key);
        if (ts == null) return null;
        if (System.currentTimeMillis() - ts[0] > ttlMs) return null;
        return dataMap.get(key);
    }

    public void put(String key, V value) {
        tsMap.put(key, new long[]{System.currentTimeMillis()});
        dataMap.put(key, value);
    }
}
