package com.sayan.zapfile.common;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory fixed-window rate limiter. Each key (client IP, user id,
 * ...) gets at most {@code maxRequests} per window; the counter resets when a
 * new window starts. Stale windows are swept opportunistically on access so
 * the map does not grow forever. Single-instance only — fine for this app,
 * which runs as one Render service.
 */
public class RateLimiter {

    private static final class Window {
        final long startedAt;
        final AtomicInteger count = new AtomicInteger();

        Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }

    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastEvictionAt = System.currentTimeMillis();

    public RateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
    }

    /** Returns true if the call identified by {@code key} fits in the current window. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        evictStale(now);
        Window window = windows.compute(key, (k, existing) ->
                existing == null || now - existing.startedAt >= windowMillis ? new Window(now) : existing);
        return window.count.incrementAndGet() <= maxRequests;
    }

    private void evictStale(long now) {
        if (now - lastEvictionAt < windowMillis) {
            return;
        }
        lastEvictionAt = now;
        windows.entrySet().removeIf(e -> now - e.getValue().startedAt >= windowMillis);
    }
}
