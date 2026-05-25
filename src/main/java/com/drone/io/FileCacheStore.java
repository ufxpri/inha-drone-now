package com.drone.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class FileCacheStore {
    private final Path root;
    private final ObjectMapper mapper;

    public FileCacheStore() {
        this(Path.of("./cache"));
    }

    public FileCacheStore(Path root) {
        this.root = root;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create cache directory: " + root, e);
        }
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Path file = pathFor(key);
        if (!Files.exists(file)) return Optional.empty();
        try {
            JavaType jt = mapper.getTypeFactory().constructParametricType(CacheEntry.class, type);
            CacheEntry<T> entry = mapper.readValue(file.toFile(), jt);
            if (entry == null || entry.isExpired()) return Optional.empty();
            return Optional.ofNullable(entry.value());
        } catch (IOException e) {
            System.err.println("Cache read failed for " + key + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public <T> void put(String key, T value, Duration ttl) {
        Path file = pathFor(key);
        CacheEntry<T> entry = new CacheEntry<>(Instant.now(), ttl, value);
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), entry);
        } catch (IOException e) {
            System.err.println("Cache write failed for " + key + ": " + e.getMessage());
        }
    }

    public void invalidate(String key) {
        try {
            Files.deleteIfExists(pathFor(key));
        } catch (IOException e) {
            System.err.println("Cache invalidate failed for " + key + ": " + e.getMessage());
        }
    }

    private Path pathFor(String key) {
        String safe = key.replaceAll("[^A-Za-z0-9._-]", "_");
        return root.resolve(safe + ".json");
    }

    public record CacheEntry<T>(Instant savedAt, Duration ttl, T value) {
        @JsonCreator
        public CacheEntry(
                @JsonProperty("savedAt") Instant savedAt,
                @JsonProperty("ttl") Duration ttl,
                @JsonProperty("value") T value) {
            this.savedAt = savedAt;
            this.ttl = ttl;
            this.value = value;
        }

        public boolean isExpired() {
            if (savedAt == null || ttl == null) return true;
            return Instant.now().isAfter(savedAt.plus(ttl));
        }
    }
}
