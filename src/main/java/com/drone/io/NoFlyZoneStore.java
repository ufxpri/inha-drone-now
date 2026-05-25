package com.drone.io;

import com.drone.model.dto.NoFlyZoneInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 번들된 GeoJSON 항공 공역(비행금지/제한/관제권/드론허용)을 클래스패스에서 한 번 로드해
 * WGS84 폴리곤으로 보관하고, 그리기/점-포함 판정에 제공한다.
 *
 * <p>원본 좌표는 EPSG:3857(Web Mercator, 미터)이며 로드 시 WGS84(위경도)로 변환한다.
 * 라이브 V-World 질의 대신 이 번들 데이터를 사용해 지도에 그려진 것과 판정이 정확히 일치한다.
 */
public final class NoFlyZoneStore {

    /** 공역 레이어 분류: 한글 라벨 + 표시 색 + 비행금지 여부. */
    public enum Category {
        PROHIBITED("비행금지구역", new Color(220, 0, 0), true),
        RESTRICTED("비행제한구역", new Color(230, 140, 0), true),
        CONTROL("관제권", new Color(40, 90, 200), true),
        DANGER("위험지역", new Color(140, 0, 0), true),
        TEMP("임시비행금지구역", new Color(220, 40, 120), true),
        ALERT("경계구역", new Color(150, 60, 180), false),
        OBSTACLE("장애물공역", new Color(110, 110, 110), false),
        COORDINATION("사전협의구역", new Color(0, 150, 150), false),
        DRONE_ALLOWED("드론 전용공역", new Color(30, 150, 60), false);

        public final String label;
        public final Color color;
        public final boolean noFly;

        Category(String label, Color color, boolean noFly) {
            this.label = label;
            this.color = color;
            this.noFly = noFly;
        }
    }

    /** 단일 폴리곤(외곽 링만). ring의 각 원소는 {lat, lon}. */
    public record ZonePolygon(String name, Category category, List<double[]> ring) {
    }

    /** 레이어별 리소스 경로 + 이름 속성 필드 + 분류. */
    private record LayerSpec(String resource, String nameField, Category category) {
    }

    private static final LayerSpec[] LAYERS = {
            new LayerSpec("/geojson/lt_c_aisprhc.geojson", "prh_lbl_1", Category.PROHIBITED),
            new LayerSpec("/geojson/lt_c_aisresc.geojson", "res_lbl_1", Category.RESTRICTED),
            new LayerSpec("/geojson/lt_c_aisctrc.geojson", "ctr_lbl_1", Category.CONTROL),
            new LayerSpec("/geojson/lt_c_aisdngc.geojson", "dng_lbl_1", Category.DANGER),
            new LayerSpec("/geojson/lt_c_aistemp.geojson", "prh_lbl_1", Category.TEMP),
            new LayerSpec("/geojson/lt_c_aisaltc.geojson", "alt_lbl_1", Category.ALERT),
            new LayerSpec("/geojson/lt_c_aisobls.geojson", "remarks_tx", Category.OBSTACLE),
            new LayerSpec("/geojson/lt_c_aispca.geojson", "nm_kor", Category.COORDINATION),
            new LayerSpec("/geojson/lt_c_aisdronezone.geojson", "name", Category.DRONE_ALLOWED),
    };

    private static final double WEB_MERCATOR_MAX = 20037508.34;

    private final ObjectMapper mapper = new ObjectMapper();
    private List<ZonePolygon> polygons;

    /** 모든 로드된 폴리곤(그리기용). 최초 호출 시 한 번 로드/캐시한다. */
    public synchronized List<ZonePolygon> all() {
        if (polygons == null) {
            polygons = load();
        }
        return polygons;
    }

    /**
     * 주어진 위경도를 포함하는 모든 공역을 ray-casting 점-포함 판정으로 수집한다.
     * intersects는 noFly 분류(비행금지/제한/관제권) 중 하나라도 매칭되면 true.
     * 드론 허용공역 매칭은 목록에는 포함되지만 intersects를 단독으로 true로 만들지 않는다.
     */
    public NoFlyZoneInfo zonesContaining(double lat, double lon) {
        List<NoFlyZoneInfo.Zone> matched = new ArrayList<>();
        boolean intersects = false;
        for (ZonePolygon p : all()) {
            if (pointInRing(lat, lon, p.ring())) {
                matched.add(new NoFlyZoneInfo.Zone(p.name(), p.category().label));
                if (p.category().noFly) {
                    intersects = true;
                }
            }
        }
        return new NoFlyZoneInfo(matched, intersects);
    }

    private List<ZonePolygon> load() {
        List<ZonePolygon> result = new ArrayList<>();
        for (LayerSpec spec : LAYERS) {
            try (InputStream in = getClass().getResourceAsStream(spec.resource())) {
                if (in == null) {
                    System.err.println("[NoFlyZoneStore] 리소스 없음, 건너뜀: " + spec.resource());
                    continue;
                }
                JsonNode root = mapper.readTree(in);
                JsonNode features = root.path("features");
                for (JsonNode feature : features) {
                    String name = feature.path("properties").path(spec.nameField()).asText("");
                    if (name.isBlank()) {
                        name = spec.category().label;
                    }
                    JsonNode coords = feature.path("geometry").path("coordinates");
                    // MultiPolygon: coordinates = [ polygon ][ ring ][ point ][ x, y ]
                    for (JsonNode polygon : coords) {
                        if (!polygon.isArray() || polygon.isEmpty()) {
                            continue;
                        }
                        JsonNode outerRing = polygon.get(0); // 외곽 링만, 홀은 무시
                        List<double[]> ring = new ArrayList<>(outerRing.size());
                        for (JsonNode pt : outerRing) {
                            double x = pt.get(0).asDouble();
                            double y = pt.get(1).asDouble();
                            ring.add(new double[]{mercatorToLat(y), mercatorToLon(x)});
                        }
                        if (ring.size() >= 3) {
                            result.add(new ZonePolygon(name, spec.category(), ring));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[NoFlyZoneStore] 로드 실패 (" + spec.resource() + "): " + e.getMessage());
            }
        }
        return result;
    }

    private static double mercatorToLon(double x) {
        return x / WEB_MERCATOR_MAX * 180.0;
    }

    private static double mercatorToLat(double y) {
        return Math.toDegrees(2 * Math.atan(Math.exp(Math.toRadians(y / WEB_MERCATOR_MAX * 180.0))) - Math.PI / 2);
    }

    /** 표준 ray-casting 점-포함 판정. lon을 x, lat을 y로 다룬다. */
    private static boolean pointInRing(double lat, double lon, List<double[]> ring) {
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = ring.get(i)[0], xi = ring.get(i)[1];
            double yj = ring.get(j)[0], xj = ring.get(j)[1];
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
