package com.kilikili.web.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 L1 缓存：Redis 抖动或短时不可用时仍能返回最近的热数据，
 * 同时把热点 key 的 Redis 读流量挡在本地。TTL 很短，可容忍多实例间的短暂不一致。
 * 不做成 Spring Bean，由使用方按自己的值类型实例化，避免泛型注入歧义。
 */
public class LocalTtlCache<V> {
    private static final int MAX_ENTRIES = 1000;

    private record Entry<V>(V value, long expireAt) {
    }

    private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();

    public V get(String key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expireAt()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public void put(String key, V value, long ttlMs) {
        if (entries.size() > MAX_ENTRIES) {
            // 简单容量保护：清掉已过期项，仍超限则整体清空，避免内存无界增长
            entries.entrySet().removeIf(item -> System.currentTimeMillis() > item.getValue().expireAt());
            if (entries.size() > MAX_ENTRIES) entries.clear();
        }
        entries.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMs));
    }

    public void remove(String key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
