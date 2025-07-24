package com.uetty.common.tool.core.cache;

import com.uetty.common.tool.core.cache.mo.Lock;

import java.util.List;

/**
 * 缓存引擎
 */
public interface CacheEngine {

    String cacheType();

    /**
     * 存放一个缓存对象
     *
     * @param cacheName      key
     * @param obj            object
     * @param expirationMillis 缓存时间，如果过期时间没有被定义，则默认30分钟，否则按照定义时间
     */
    <T> void put(String cacheName, T obj, Long expirationMillis);


    /**
     * 取出一个缓存对象
     *
     */
    <T> T get(String cacheName);

    void removeAll();

    /**
     * 删除某个缓存
     *
     * @param cacheName
     */
    void remove(String cacheName);

    <T> void remove(String cacheName, T data);

    void updateExpiration(String cacheName, long expirationMillis);

    /**
     * 检查缓存对象是否存在，
     * 若不存在，则返回false
     * 若存在，检查其是否已过有效期，如果已经过了则删除该缓存并返回false
     *
     * @param cacheName
     * @return
     */
    boolean checkExists(String cacheName);

    List<String> scanCachePrefix(String cachePrefix);

    long getExpireSeconds(String cacheName);

    Lock lock(String key, int autoReleaseSeconds);

    Lock lock(String key);
}
