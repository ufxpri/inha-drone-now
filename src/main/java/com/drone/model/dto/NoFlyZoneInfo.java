package com.drone.model.dto;

import java.util.List;

public record NoFlyZoneInfo(List<Zone> zones, boolean intersects) {
    public record Zone(String name, String type) {
    }
}
