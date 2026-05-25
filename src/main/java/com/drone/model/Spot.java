package com.drone.model;

import java.time.LocalDateTime;

public record Spot(String name, Location location, LocalDateTime savedAt) {
}
