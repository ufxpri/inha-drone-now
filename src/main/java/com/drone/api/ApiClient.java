package com.drone.api;

import com.drone.model.Location;

import java.time.Duration;
import java.time.LocalDateTime;

public interface ApiClient<T> {
    String name();
    Duration ttl();
    String cacheKey(Location loc, LocalDateTime time);
    T fetch(Location loc, LocalDateTime time) throws Exception;
}
