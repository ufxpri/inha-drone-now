package com.drone.judge;

import com.drone.judge.FlightSafetyReport.ChecklistItem;
import com.drone.judge.FlightSafetyReport.Verdict;
import com.drone.model.GeoUtil;
import com.drone.model.Location;
import com.drone.model.PilotLicense;
import com.drone.model.dto.NoFlyZoneInfo;
import com.drone.model.dto.NotamInfo;
import com.drone.model.dto.SunTimeInfo;
import com.drone.model.dto.WeatherInfo;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class FlightSafetyJudge {

    public FlightSafetyReport judge(PilotLicense license, Location loc, LocalDateTime when,
                                    WeatherInfo w, NotamInfo n, NoFlyZoneInfo z, SunTimeInfo s) {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(checkWind(w));
        items.add(checkRain(w));
        items.add(checkNotam(n, loc));
        items.add(checkNoFlyZone(z));
        items.add(checkDaylight(s, when));
        items.add(checkLicense(license));
        return new FlightSafetyReport(items, aggregate(items), LocalDateTime.now());
    }

    private ChecklistItem checkWind(WeatherInfo w) {
        if (w == null) return new ChecklistItem("바람", Verdict.CAUTION, "정보 없음");
        double v = w.windSpeed();
        if (v > 12) return new ChecklistItem("바람", Verdict.NO_GO, String.format("풍속 %.1f m/s (>12)", v));
        if (v > 8) return new ChecklistItem("바람", Verdict.CAUTION, String.format("풍속 %.1f m/s (>8)", v));
        return new ChecklistItem("바람", Verdict.GO, String.format("풍속 %.1f m/s", v));
    }

    private ChecklistItem checkRain(WeatherInfo w) {
        if (w == null) return new ChecklistItem("강수", Verdict.CAUTION, "정보 없음");
        if (w.rainfall() > 0) return new ChecklistItem("강수", Verdict.NO_GO, String.format("강수 %.1f mm", w.rainfall()));
        return new ChecklistItem("강수", Verdict.GO, "강수 없음");
    }

    private ChecklistItem checkNotam(NotamInfo n, Location loc) {
        if (n == null) return new ChecklistItem("NOTAM", Verdict.CAUTION, "정보 없음");
        List<NotamInfo.NotamItem> all = n.items();
        if (all == null || all.isEmpty()) {
            return new ChecklistItem("NOTAM", Verdict.GO, "발효 중인 NOTAM 없음");
        }
        if (loc == null) {
            return new ChecklistItem("NOTAM", Verdict.CAUTION, all.size() + "건 — 직접 확인 필요");
        }

        int prohibitedHits = 0;   // 클릭 지점이 비행금지/제한 NOTAM 원 내부
        int otherHits = 0;        // 그 외 NOTAM 원 내부
        int nearby = 0;           // 약 10km 이내 근접
        for (NotamInfo.NotamItem it : all) {
            if (!it.hasGeometry()) continue;
            double cLat = it.centerLat(), cLon = it.centerLon();
            if (GeoUtil.withinCircle(loc.lat(), loc.lon(), cLat, cLon, it.radiusNm())) {
                if (it.prohibited()) prohibitedHits++; else otherHits++;
            } else if (GeoUtil.haversineKm(loc.lat(), loc.lon(), cLat, cLon) <= 10.0) {
                nearby++;
            }
        }

        if (prohibitedHits > 0) {
            return new ChecklistItem("NOTAM", Verdict.NO_GO,
                    "비행금지/제한 NOTAM 구역 내 (" + prohibitedHits + "건)");
        }
        if (otherHits > 0) {
            return new ChecklistItem("NOTAM", Verdict.CAUTION,
                    "NOTAM 구역 내 (" + otherHits + "건)");
        }
        if (nearby > 0) {
            return new ChecklistItem("NOTAM", Verdict.CAUTION,
                    "주변 NOTAM " + nearby + "건 확인 필요");
        }
        return new ChecklistItem("NOTAM", Verdict.GO, "주변 NOTAM 없음");
    }

    private ChecklistItem checkNoFlyZone(NoFlyZoneInfo z) {
        if (z == null) return new ChecklistItem("비행금지구역", Verdict.CAUTION, "정보 없음");
        if (z.intersects()) {
            return new ChecklistItem("비행금지구역", Verdict.NO_GO, "금지구역 " + z.zones().size() + "건 교차");
        }
        return new ChecklistItem("비행금지구역", Verdict.GO, "교차 없음");
    }

    private ChecklistItem checkDaylight(SunTimeInfo s, LocalDateTime when) {
        if (s == null) return new ChecklistItem("주간 비행", Verdict.CAUTION, "일출/일몰 정보 없음");
        LocalTime t = when.toLocalTime();
        if (t.isBefore(s.sunrise()) || t.isAfter(s.sunset())) {
            return new ChecklistItem("주간 비행", Verdict.NO_GO,
                    "야간 (" + s.sunrise() + " ~ " + s.sunset() + ")");
        }
        return new ChecklistItem("주간 비행", Verdict.GO,
                "주간 (" + s.sunrise() + " ~ " + s.sunset() + ")");
    }

    private ChecklistItem checkLicense(PilotLicense l) {
        if (l == null || l == PilotLicense.NONE) {
            return new ChecklistItem("조종 자격", Verdict.CAUTION, "자격 없음 — 250g 이하 비사업용만 가능");
        }
        return new ChecklistItem("조종 자격", Verdict.GO, l.getDisplayName() + " — " + l.getAllowedDroneSpec());
    }

    private Verdict aggregate(List<ChecklistItem> items) {
        boolean caution = false;
        for (ChecklistItem it : items) {
            if (it.status() == Verdict.NO_GO) return Verdict.NO_GO;
            if (it.status() == Verdict.CAUTION) caution = true;
        }
        return caution ? Verdict.CAUTION : Verdict.GO;
    }
}
