package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;

import java.time.LocalDateTime;
import java.util.Optional;

public abstract class CachedApiClient<T> implements ApiClient<T> {
    protected final FileCacheStore cache;
    private ApiStatusBus statusBus;

    protected CachedApiClient(FileCacheStore cache) {
        this.cache = cache;
    }

    /** 풋터 상태바에 캐시/실시간/실패를 보고할 버스(없으면 보고하지 않음). */
    public void setStatusBus(ApiStatusBus bus) {
        this.statusBus = bus;
    }

    private void report(ApiStatus status, String detail) {
        if (statusBus != null) statusBus.report(name(), status, detail);
    }

    public T get(Location loc, LocalDateTime time) {
        String key = cacheKey(loc, time);
        Optional<T> hit = cache.get(key, type());
        if (hit.isPresent()) {
            report(ApiStatus.CACHED, "캐시 적중 (TTL " + ttl().toMinutes() + "분)");
            return hit.get();
        }
        try {
            T fresh = fetch(loc, time);
            if (fresh != null) cache.put(key, fresh, ttl());
            report(ApiStatus.LIVE, fresh != null ? "실시간 수신" : "응답 없음");
            return fresh;
        } catch (Exception e) {
            report(ApiStatus.FAILED, e.getMessage());
            throw new RuntimeException("[" + name() + "] API 호출 실패: " + e.getMessage(), e);
        }
    }

    protected abstract Class<T> type();
}
