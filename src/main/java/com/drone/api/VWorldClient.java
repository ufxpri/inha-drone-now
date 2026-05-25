package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;
import com.drone.model.dto.NoFlyZoneInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class VWorldClient extends CachedApiClient<NoFlyZoneInfo> {
    private static final String ENDPOINT = "https://api.vworld.kr/req/data";
    private final String serviceKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public VWorldClient(FileCacheStore cache, String serviceKey) {
        super(cache);
        this.serviceKey = serviceKey;
    }

    @Override public String name() { return "비행금지구역"; }
    @Override public Duration ttl() { return Duration.ofDays(365); }
    @Override protected Class<NoFlyZoneInfo> type() { return NoFlyZoneInfo.class; }

    @Override
    public String cacheKey(Location loc, LocalDateTime time) {
        return "vworld_" + loc.lat() + "_" + loc.lon();
    }

    // 드론 비행 가부와 직결된 항공 공역 레이어 (데이터 API 코드 → 표시 종류)
    private static final String[][] LAYERS = {
            {"LT_C_AISPRHC", "비행금지구역"},
            {"LT_C_AISRESC", "비행제한구역"},
            {"LT_C_AISCTRC", "관제권"},
    };

    @Override
    public NoFlyZoneInfo fetch(Location loc, LocalDateTime time) throws Exception {
        // geomFilter의 POINT는 WKT(공백 구분). 점-포함 질의로 클릭 지점이 공역 내부인지 판정.
        String geom = "POINT(" + loc.lon() + " " + loc.lat() + ")";
        String encGeom = URLEncoder.encode(geom, StandardCharsets.UTF_8);
        String encKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);

        List<NoFlyZoneInfo.Zone> zones = new ArrayList<>();
        for (String[] layer : LAYERS) {
            zones.addAll(queryLayer(layer[0], layer[1], encGeom, encKey));
        }
        return new NoFlyZoneInfo(zones, !zones.isEmpty());
    }

    private List<NoFlyZoneInfo.Zone> queryLayer(String data, String typeLabel,
                                                String encGeom, String encKey) throws Exception {
        String url = ENDPOINT
                + "?service=data&request=GetFeature&data=" + data
                + "&key=" + encKey
                + "&format=json&errorformat=json&geometry=false"
                + "&geomFilter=" + encGeom + "&size=50";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = mapper.readTree(resp.body());
        String status = root.path("response").path("status").asText("");
        if (!"OK".equalsIgnoreCase(status)) {
            // NOT_FOUND(해당 지점에 공역 없음) 등은 정상 빈 결과로 처리
            return List.of();
        }

        List<NoFlyZoneInfo.Zone> zones = new ArrayList<>();
        JsonNode features = root.path("response").path("result").path("featureCollection").path("features");
        if (features.isArray()) {
            for (JsonNode f : features) {
                String name = firstStringProperty(f.path("properties"), typeLabel);
                zones.add(new NoFlyZoneInfo.Zone(name, typeLabel));
            }
        }
        return zones;
    }

    /** 레이어마다 라벨 필드명이 달라(prh_lbl_1, ctr_lbl_1 등) 첫 비어있지 않은 문자열 속성을 이름으로 사용. */
    private static String firstStringProperty(JsonNode props, String fallback) {
        var it = props.fields();
        while (it.hasNext()) {
            var e = it.next();
            String v = e.getValue().asText("");
            if (v != null && !v.isBlank() && !v.startsWith("<")) {
                return v;
            }
        }
        return fallback;
    }
}
