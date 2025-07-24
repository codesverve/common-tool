package com.uetty.common.tool.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.Getter;
import org.checkerframework.checker.index.qual.NonNegative;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MemCacheManager {

    private static final Cache<String, Expirable<Object>> CACHE_CONTAINER = createCacheContainer();

    private static Cache<String, Expirable<Object>> createCacheContainer() {
        return Caffeine.newBuilder()
                .expireAfter(new Expiry<String, Expirable<Object>>() {
                    @Override
                    public long expireAfterCreate(String key, Expirable<Object> value, long currentTime) {
                        return value.getExpireNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Expirable<Object> value, long currentTime, @NonNegative long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, Expirable<Object> value, long currentTime, @NonNegative long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    private static <T> Expirable<T> decorate(T obj, int expirationSeconds) {
        return new Expirable<>(obj, expirationSeconds, TimeUnit.SECONDS);
    }

    private static <T> T revert(Expirable<T> expirable) {
        if (expirable != null) {
            return expirable.getValue();
        }
        return null;
    }

    public static <T> void put(String cacheName, T obj, int expirationSeconds) {
        CACHE_CONTAINER.put(cacheName, decorate(obj, expirationSeconds));
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String cacheName) {
        Expirable<Object> expirable = CACHE_CONTAINER.getIfPresent(cacheName);
        return (T) revert(expirable);
    }

    @SuppressWarnings("unchecked")
    public static <T> T compute(String cacheName, Function<String, ? extends T> function, int expirationSeconds) {
        Expirable<Object> expirable = CACHE_CONTAINER.get(cacheName, function.andThen(data -> decorate(data, expirationSeconds)));
        return (T) revert(expirable);
    }

    public static int size() {
        return (int) CACHE_CONTAINER.estimatedSize();
    }

    public static void expireAll() {
        CACHE_CONTAINER.invalidateAll();
    }

    public static Map<String, Object> asMap() {
        ConcurrentMap<String, Expirable<Object>> map = CACHE_CONTAINER.asMap();
        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> revert(entry.getValue())));
    }


    @Getter
    private static class Expirable<T> {
        private final long expireNanos;

        private final T value;

        public Expirable(T value, long duration, TimeUnit unit) {
            this.value = value;
            this.expireNanos = unit.toNanos(duration);
        }
    }

    public static void main(String[] args) throws InterruptedException {

        String aa = get("aa");
        System.out.println("预期：null，实际：" + aa);

        put("aa", "bb", 10);
        System.out.println("预期：bb，实际：" + get("aa"));
        Thread.sleep(1000);

        System.out.println("预期：bb，实际：" + get("aa"));

        Thread.sleep(10000);
        System.out.println("预期：null，实际：" + get("aa"));

        put("num", 2, 10);
        System.out.println("预期：2，实际：" + get("num"));

        Thread.sleep(3000);
        System.out.println("预期：2，实际：" + get("num"));

        put("num", 3, 10);
        System.out.println("预期：3，实际：" + get("num"));

        String comp = get("comp");
        System.out.println("预期：null，实际：" + comp);

        System.out.println("预期：computed data，实际：" + compute("comp", k -> "computed data", 10));

        Thread.sleep(8500);
        System.out.println("预期：computed data，实际：" + compute("comp", k -> "computed data rewrite", 10));

        Thread.sleep(1000);
        System.out.println("预期：computed data，实际：" + get("comp"));

        Thread.sleep(4000);
        System.out.println("预期：null，实际：" + get("comp"));

        System.out.println("预期：null，实际：" + get("num"));
    }
}
