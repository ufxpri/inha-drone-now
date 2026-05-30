package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;
import com.drone.model.dto.RainForecastInfo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 기상청 단기예보(getVilageFcst)에서 시간대별 강수확률(POP)·강수형태(PTY)를 뽑아온다.
 * 초단기실황(현재값)과 달리 향후 몇 시간의 시계열을 제공한다.
 *   - 발표시각: 02,05,08,11,14,17,20,23시 (각 10분경 발표) → 보수적으로 45분 버퍼 후 직전 회차 사용.
 *   - 위경도→격자 변환은 WeatherClient.toGrid 재사용.
 */
public class RainForecastClient extends CachedApiClient<RainForecastInfo> {
    private static final String ENDPOINT =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int MAX_HOURS = 8;
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FCST = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final String serviceKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RainForecastClient(FileCacheStore cache, String serviceKey) {
        super(cache);
        this.serviceKey = serviceKey;
    }

    @Override public String name() { return "강수예보"; }
    @Override public Duration ttl() { return Duration.ofMinutes(30); }
    @Override protected Class<RainForecastInfo> type() { return RainForecastInfo.class; }

    @Override
    public String cacheKey(Location loc, LocalDateTime time) {
        int[] g = WeatherClient.toGrid(loc.lat(), loc.lon());
        LocalDateTime base = baseDateTime(time);
        return "rainfc_" + g[0] + "_" + g[1] + "_" + base.format(FCST);
    }

    @Override
    public RainForecastInfo fetch(Location loc, LocalDateTime time) throws Exception {
        int[] g = WeatherClient.toGrid(loc.lat(), loc.lon());
        LocalDateTime base = baseDateTime(time);

        String url = ENDPOINT + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&numOfRows=300&pageNo=1&dataType=JSON"
                + "&base_date=" + base.format(YMD)
                + "&base_time=" + String.format("%02d00", base.getHour())
                + "&nx=" + g[0] + "&ny=" + g[1];

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String raw = resp.body() == null ? "" : resp.body().trim();
        // 트래픽 급증 시 data.go.kr이 JSON 대신 평문/HTML 오류를 200으로 반환하는 경우가 있다.
        if (!raw.startsWith("{")) {
            throw new RuntimeException("비정상 응답(JSON 아님): "
                    + raw.substring(0, Math.min(80, raw.length())));
        }
        JsonNode root = mapper.readTree(raw);
        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asText("");
        if (!"00".equals(code)) {
            throw new RuntimeException("기상청 응답 오류: " + header.path("resultMsg").asText());
        }

        // 예보시각별로 POP(=[0]), PTY(=[1])를 모은다.
        TreeMap<LocalDateTime, int[]> byTime = new TreeMap<>();
        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (items.isArray()) {
            for (JsonNode it : items) {
                String cat = it.path("category").asText();
                if (!"POP".equals(cat) && !"PTY".equals(cat)) continue;
                LocalDateTime ft = parseFcstTime(it.path("fcstDate").asText(), it.path("fcstTime").asText());
                if (ft == null) continue;
                int[] arr = byTime.computeIfAbsent(ft, k -> new int[]{-1, -1});
                int v = parseInt(it.path("fcstValue").asText());
                if ("POP".equals(cat)) arr[0] = v; else arr[1] = v;
            }
        }

        LocalDateTime cutoff = time.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        List<RainForecastInfo.Hourly> hours = new ArrayList<>();
        for (var entry : byTime.entrySet()) {
            if (entry.getKey().isBefore(cutoff)) continue;
            int pop = entry.getValue()[0];
            if (pop < 0) continue;  // POP 없는 시각은 건너뜀
            int pty = Math.max(entry.getValue()[1], 0);
            hours.add(new RainForecastInfo.Hourly(entry.getKey().toLocalTime(), pop, pty));
            if (hours.size() >= MAX_HOURS) break;
        }
        return new RainForecastInfo(hours);
    }

    /** 발표시각 10분 + 안전 버퍼를 고려해 45분 이전 기준으로 직전 발표 회차를 고른다. */
    private static LocalDateTime baseDateTime(LocalDateTime t) {
        LocalDateTime adj = t.minusMinutes(45);
        int hour = adj.getHour();
        int chosen = -1;
        for (int h : BASE_HOURS) {
            if (h <= hour) chosen = h;
        }
        if (chosen == -1) {
            // 02:45 이전 → 전날 23시 발표
            return adj.toLocalDate().minusDays(1).atTime(23, 0);
        }
        return adj.toLocalDate().atTime(chosen, 0);
    }

    private static LocalDateTime parseFcstTime(String date, String time) {
        if (date == null || time == null || date.length() != 8 || time.length() != 4) return null;
        try {
            return LocalDateTime.parse(date + time, FCST);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
