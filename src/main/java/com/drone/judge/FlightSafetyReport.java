package com.drone.judge;

import java.time.LocalDateTime;
import java.util.List;

public record FlightSafetyReport(List<ChecklistItem> items, Verdict overall, LocalDateTime evaluatedAt) {
    public record ChecklistItem(String label, Verdict status, String detail) {
    }

    public enum Verdict { GO, CAUTION, NO_GO }
}
