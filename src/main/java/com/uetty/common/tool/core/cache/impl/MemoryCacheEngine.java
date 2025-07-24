package com.uetty.common.tool.core.cache.impl;

import com.uetty.common.tool.core.cache.CacheEngine;
import com.uetty.common.tool.core.cache.CacheManager;
import com.uetty.common.tool.core.cache.mo.Lock;
import com.uetty.common.tool.core.string.StringUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 本地内存缓存引擎（仅适用于单实例）
 */
@Slf4j
public class MemoryCacheEngine implements CacheEngine {

    @Override
    public String cacheType() {
        return CacheManager.CACHE_TYPE_MEMORY;
    }

    private static final ConcurrentHashMap<String, AutoExpireData<?>> CACHE_MAP = new ConcurrentHashMap<>();

    private static final String CACHE_PREFIX = "cache:";
    private static final String LOCK_PREFIX = "lock:";

    private static final ReentrantLock reentrantLock = new ReentrantLock();

    /**
     * 默认每个缓存生效时间30分钟
     */
    public static final long EXPIRE_SECONDS = 30 * 60;

    /**
     * 缓存自动清理单线程定时线程池
     */
    private static final ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(1, r -> new Thread("memory cache clear thread"));

    static {
        startTask();
    }

    private static synchronized void startTask() {
        ClearTask clearTask = new ClearTask();
        executorService.schedule(clearTask, 900, TimeUnit.MILLISECONDS);
    }

    private String getCacheKey(String cacheName) {
        return CACHE_PREFIX + cacheName;
    }

    private String getLockKey(String lockName) {
        return LOCK_PREFIX + lockName;
    }

    /**
     * 存放一个缓存对象
     *
     * @param cacheName      key
     * @param obj            object
     * @param expirationMillis 缓存时间（秒，默认30分钟）
     */
    @Override
    public <T> void put(String cacheName, T obj, Long expirationMillis) {
        if (obj == null) {
            return;
        }
        if (expirationMillis == null) {
            expirationMillis = EXPIRE_SECONDS * 1000L;
        }

        AutoExpireData<T> cache = new AutoExpireData<>(obj);
        cache.setAutoExpiredMillis(expirationMillis);
        CACHE_MAP.put(getCacheKey(cacheName), cache);
    }


    /**
     * 取出一个缓存对象
     *
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String cacheName) {

        AutoExpireData<?> autoExpireData = CACHE_MAP.get(getCacheKey(cacheName));

        if (autoExpireData == null) {
            return null;
        }

        return (T) autoExpireData.getData();
    }

    @Override
    public void removeAll() {
        Map<String, AutoExpireData<?>> map = new HashMap<>(CACHE_MAP);
        List<Map.Entry<String, AutoExpireData<?>>> entryList = map.entrySet().stream()
                .filter(entry -> StringUtil.startsWith(entry.getKey(), CACHE_PREFIX))
                .collect(Collectors.toList());
        for (Map.Entry<String, AutoExpireData<?>> entry : entryList) {
            CACHE_MAP.remove(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 删除某个缓存
     *
     */
    @Override
    public void remove(String cacheName) {
        CACHE_MAP.remove(getCacheKey(cacheName));
    }

    @Override
    public <T> void remove(String cacheName, T data) {
        AutoExpireData<?> autoExpireData = CACHE_MAP.get(getCacheKey(cacheName));
        if (autoExpireData == null) {
            return;
        }
        Object data1 = autoExpireData.getData();
        if (Objects.equals(data1, data)) {
            CACHE_MAP.remove(getCacheKey(cacheName), autoExpireData);
        }
    }

    @Override
    public void updateExpiration(String cacheName, long expirationMillis) {

        AutoExpireData<?> autoExpireData = CACHE_MAP.get(getCacheKey(cacheName));

        if (autoExpireData == null) {
            return;
        }

        long createTime = autoExpireData.getCreateTime();
        long currentTimeMillis = System.currentTimeMillis();
        // 从当前时间开始算过期时间
        expirationMillis = currentTimeMillis + expirationMillis - createTime;

        autoExpireData.setAutoExpiredMillis(expirationMillis);
    }

    /**
     * 检查缓存对象是否存在，
     * 若不存在，则返回false
     * 若存在，检查其是否已过有效期，如果已经过了则删除该缓存并返回false
     *
     */
    @Override
    public boolean checkExists(String cacheName) {
        return get(getCacheKey(cacheName)) != null;
    }

    @Override
    public List<String> scanCachePrefix(String cachePrefix) {
        String cacheKeyPrefix = getCacheKey(cachePrefix);

        List<String> keys = new ArrayList<>(CACHE_MAP.keySet());
        return keys.stream()
                .filter(key -> StringUtil.startsWith(key, cacheKeyPrefix))
                .map(key -> key.substring(CACHE_PREFIX.length()))
                .collect(Collectors.toList());
    }

    @Override
    public long getExpireSeconds(String cacheName) {
        AutoExpireData<?> autoExpireData = CACHE_MAP.get(getCacheKey(cacheName));

        if (autoExpireData == null) {
            return -1;
        }

        long createTime = autoExpireData.createTime;
        long autoExpiredMillis = autoExpireData.autoExpiredMillis;
        long currentTimeMillis = System.currentTimeMillis();

        long restMillis = createTime + autoExpiredMillis - currentTimeMillis;

        return restMillis >= 0 ? restMillis / 1000 : -1;
    }

    public static class AutoExpireData<T> {

        private final T data;

        @Getter
        private final long createTime;

        @Setter
        private long autoExpiredMillis = EXPIRE_SECONDS * 1000;


        public AutoExpireData(T data) {
            this.data = data;
            this.createTime = System.currentTimeMillis();
        }

        public long getAutoExpiredSeconds() {
            return this.autoExpiredMillis / 1000;
        }

        public void setAutoExpiredSeconds(long autoExpiredSeconds) {
            this.autoExpiredMillis = autoExpiredSeconds * 1000;
        }

        public boolean checkAlive() {
            long createTime = this.createTime;
            long autoExpiredMillis = this.autoExpiredMillis;
            long currentTimeMillis = System.currentTimeMillis();

            long pass = currentTimeMillis - createTime;
            return pass < autoExpiredMillis;
        }

        public void clear() {
            this.setAutoExpiredMillis(0);
        }

        public T getData() {
            boolean alive = checkAlive();
            if (!alive) {
                return null;
            }
            return data;
        }
    }

    static class ClearTask implements Runnable {

        @Override
        public void run() {
            Map<String, AutoExpireData<?>> map = new HashMap<>(CACHE_MAP);
            try {
                for (Map.Entry<String, AutoExpireData<?>> entry : map.entrySet()) {

                    String key = entry.getKey();
                    AutoExpireData<?> value = entry.getValue();

                    if (value.checkAlive()) {
                        continue;
                    }

                    CACHE_MAP.remove(key, value);
                }

            } catch (Exception e) {
                log.error("[clear] 清理异常", e);
            }
        }
    }

    /**
     * 获取锁
     * @param key 锁名
     * @param autoReleaseSeconds 自动释放的秒数（防止宕机未释放）
     * @return 锁对象（如果未获取到锁，返回null）
     */
    public Lock lock(String key, int autoReleaseSeconds) {
        Lock lock = new MemoryCacheLock();
        lock.setKey(getLockKey(key));
        lock.setToken(UUID.randomUUID().toString());
        try {
            reentrantLock.lock();
            try {
                AutoExpireData<?> lockData = CACHE_MAP.get(lock.getKey());
                boolean alive = lockData.checkAlive();
                if (alive) {
                    // 已经被锁过了
                    return null;
                }
                AutoExpireData<String> cache = new AutoExpireData<>(lock.getToken());
                cache.setAutoExpiredMillis(autoReleaseSeconds * 1000L);
                CACHE_MAP.put(lock.getKey(), cache);
            } finally {
                reentrantLock.unlock();
            }
            return lock;
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * 获取锁(2分钟后自动释放锁，防止宕机情况未释放锁)
     * @param key 锁名
     * @return 锁对象（如果未获取到锁，返回null）
     */
    public Lock lock(String key) {
        return lock(key, 120);
    }

    public class MemoryCacheLock extends Lock {
        /**
         * 释放锁
         */
        public void release() {
            // 释放锁
            remove(getKey(), getToken());
        }
    }
}
