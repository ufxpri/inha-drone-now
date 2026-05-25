package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;

import java.time.LocalDateTime;
import java.util.Optional;

public abstract class CachedApiClient<T> implements ApiClient<T> {
    protected final FileCacheStore cache;

    protected CachedApiClient(FileCacheStore cache) {
        this.cache = cache;
    }

    public T get(Location loc, LocalDateTime time) {
        String key = cacheKey(loc, time);
        Optional<T> hit = cache.get(key, type());
        if (hit.isPresent()) return hit.get();
        try {
            T fresh = fetch(loc, time);
            if (fresh != null) cache.put(key, fresh, ttl());
            return fresh;
        } catch (Exception e) {
            throw new RuntimeException("[" + name() + "] API 호출 실패: " + e.getMessage(), e);
        }
    }

    protected abstract Class<T> type();
}
