package com.startupvalidationbot.radar.ai;

import java.util.concurrent.atomic.AtomicInteger;

public final class RadarAiRunBudget {
    private final int limit;
    private final AtomicInteger consumed = new AtomicInteger();

    public RadarAiRunBudget(int limit) {
        this.limit = Math.max(0, limit);
    }

    public boolean tryAcquire() {
        while (true) {
            int current = consumed.get();
            if (current >= limit) return false;
            if (consumed.compareAndSet(current, current + 1)) return true;
        }
    }

    public int consumed() {
        return consumed.get();
    }

    public int limit() {
        return limit;
    }
}
