package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;
import com.drone.model.dto.WeatherInfo;
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
import java.time.format.DateTimeFormatter;

public class WeatherClient extends CachedApiClient<WeatherInfo> {
    private static final String ENDPOINT =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
    private final String serviceKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public WeatherClient(FileCacheStore cache, String serviceKey) {
        super(cache);
        this.serviceKey = serviceKey;
    }

    @Override public String name() { return "기상청"; }
    @Override public Duration ttl() { return Duration.ofMinutes(10); }
    @Override protected Class<WeatherInfo> type() { return WeatherInfo.class; }

    @Override
    public String cacheKey(Location loc, LocalDateTime time) {
        int[] g = toGrid(loc.lat(), loc.lon());
        String baseTime = baseTime(time);
        return "weather_" + g[0] + "_" + g[1] + "_" + baseTime;
    }

    @Override
    public WeatherInfo fetch(Location loc, LocalDateTime time) throws Exception {
        int[] g = toGrid(loc.lat(), loc.lon());
        String baseDate = time.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = baseTime(time);

        String url = ENDPOINT + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&numOfRows=1000&pageNo=1&dataType=JSON"
                + "&base_date=" + baseDate
                + "&base_time=" + baseTime
                + "&nx=" + g[0] + "&ny=" + g[1];

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = mapper.readTree(resp.body());
        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asText("");
        if (!"00".equals(code)) {
            throw new RuntimeException("기상청 응답 오류: " + header.path("resultMsg").asText());
        }

        double wsd = 0, vec = 0, rn1 = 0, t1h = 0;
        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (items.isArray()) {
            for (JsonNode it : items) {
                String cat = it.path("category").asText();
                String val = it.path("obsrValue").asText();
                switch (cat) {
                    case "WSD" -> wsd = parseDouble(val);
                    case "VEC" -> vec = parseDouble(val);
                    case "RN1" -> rn1 = "강수없음".equals(val) ? 0 : parseDouble(val);
                    case "T1H" -> t1h = parseDouble(val);
                    default -> {}
                }
            }
        }
        return new WeatherInfo(wsd, vec, rn1, t1h);
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static String baseTime(LocalDateTime t) {
        int hh = t.getMinute() < 40 ? (t.getHour() == 0 ? 23 : t.getHour() - 1) : t.getHour();
        return String.format("%02d00", hh);
    }

    public static int[] toGrid(double lat, double lon) {
        final double RE = 6371.00877;
        final double GRID = 5.0;
        final double SLAT1 = 30.0;
        final double SLAT2 = 60.0;
        final double OLON = 126.0;
        final double OLAT = 38.0;
        final double XO = 43;
        final double YO = 136;

        double DEGRAD = Math.PI / 180.0;
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lon * DEGRAD - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;
        int x = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int y = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new int[]{x, y};
    }
}
