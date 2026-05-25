package com.drone.model;

/** 지리 계산 유틸: 거리(haversine), NM→km 변환, 원 내부 판정. */
public final class GeoUtil {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtil() {}

    /** 두 좌표 사이 대권거리(km). */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** 해리(nautical mile) → km. */
    public static double nmToKm(double nm) {
        return nm * 1.852;
    }

    /** (lat,lon)이 (cLat,cLon) 중심 반경 radiusNm(NM) 원 안에 있는지. */
    public static boolean withinCircle(double lat, double lon, double cLat, double cLon, double radiusNm) {
        return haversineKm(lat, lon, cLat, cLon) <= nmToKm(radiusNm);
    }
}
