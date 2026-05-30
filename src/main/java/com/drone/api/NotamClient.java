package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;
import com.drone.model.dto.NotamInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotamClient extends CachedApiClient<NotamInfo> {
    private static final String INIT_URL =
            "https://aim.koca.go.kr/xNotam/index.do?type=search&language=ko_KR";
    private static final String SEARCH_URL =
            "https://aim.koca.go.kr/xNotam/searchAllNotam.do?ibpage=1";
    private static final Pattern JSESSION = Pattern.compile("JSESSIONID=([^;]+)");
    // Q-line 기하 토큰: DDMM[N/S]DDDMM[E/W]RRR (예: 3804N12724E016)
    private static final Pattern Q_GEOM = Pattern.compile("(\\d{4})([NS])(\\d{5})([EW])(\\d{3})");
    // E-line 정밀 중심: RADIUS 5NM CENTERED ON 380412N1272407E (DDMMSS / DDDMMSS)
    private static final Pattern E_GEOM = Pattern.compile(
            "RADIUS\\s+(\\d+)NM\\s+CENTERED ON\\s+(\\d{6})([NS])(\\d{7})([EW])");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String sessionId;

    public NotamClient(FileCacheStore cache) {
        super(cache);
    }

    @Override public String name() { return "NOTAM"; }
    @Override public Duration ttl() { return Duration.ofHours(1); }
    @Override protected Class<NotamInfo> type() { return NotamInfo.class; }

    @Override
    public String cacheKey(Location loc, LocalDateTime time) {
        return "notam_" + time.toLocalDate();
    }

    @Override
    public NotamInfo fetch(Location loc, LocalDateTime time) throws Exception {
        ensureSession();
        String date = time.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String body = "sch_snow_series=&sch_select=&sch_inorout=D"
                + "&sch_from_date=" + date + "&sch_from_time=0000"
                + "&sch_to_date=" + date + "&sch_to_time=2359"
                + "&sch_series=&sch_notam_no=&sch_elevation_min=&sch_elevation_max="
                + "&sch_airport=&sch_qcode=&sch_fir=&sch_full_text=&iborderby=";

        HttpRequest req = HttpRequest.newBuilder(URI.create(SEARCH_URL))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", INIT_URL)
                .header("Cookie", "JSESSIONID=" + sessionId)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        List<NotamInfo.NotamItem> items = new ArrayList<>();
        JsonNode root;
        try {
            root = mapper.readTree(resp.body());
        } catch (Exception e) {
            return new NotamInfo(items);
        }
        JsonNode data = root.path("DATA");
        if (data.isArray()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
            for (JsonNode n : data) {
                String location = n.path("LOCATION").asText("");
                String fir = n.path("FIR").asText("");
                String qmean = n.path("QCODE_MEAN").asText("");
                String ecode = n.path("ECODE").asText("");
                String qcode = n.path("QCODE").asText("");
                String fullText = n.path("FULL_TEXT").asText("");
                String startRaw = n.path("EFFECTIVESTART").asText("");
                String endRaw = n.path("EFFECTIVEEND").asText("");
                LocalDateTime from = parseNotamDate(startRaw, fmt);
                LocalDateTime to = parseNotamDate(endRaw, fmt);

                Geometry g = parseGeometry(fullText);
                boolean prohibited = qcode != null && qcode.startsWith("QR");
                items.add(new NotamInfo.NotamItem(
                        location + " (" + fir + ")",
                        qmean + " — " + ecode,
                        from, to,
                        g.lat, g.lon, g.radiusNm,
                        qcode, prohibited));
            }
        }
        return new NotamInfo(items);
    }

    private void ensureSession() throws Exception {
        if (sessionId != null) return;
        HttpRequest req = HttpRequest.newBuilder(URI.create(INIT_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Optional<String> cookie = resp.headers().allValues("Set-Cookie").stream()
                .map(JSESSION::matcher)
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .findFirst();
        if (cookie.isEmpty()) {
            throw new RuntimeException("NOTAM 세션 획득 실패");
        }
        sessionId = cookie.get();
    }

    /** 파싱된 기하정보(없으면 필드 null). */
    private record Geometry(Double lat, Double lon, Double radiusNm) {
        static final Geometry NONE = new Geometry(null, null, null);
    }

    /** FULL_TEXT에서 Q-line 기하를 파싱하고, 가능하면 E-line 정밀 중심으로 덮어쓴다. */
    static Geometry parseGeometry(String fullText) {
        if (fullText == null || fullText.isBlank()) return Geometry.NONE;
        Double lat = null, lon = null, radiusNm = null;

        Matcher q = Q_GEOM.matcher(fullText);
        if (q.find()) {
            lat = dms(q.group(1), 2, q.group(2).equals("N"));   // DDMM
            lon = dms(q.group(3), 3, q.group(4).equals("E"));   // DDDMM
            int r = Integer.parseInt(q.group(5));
            radiusNm = (r == 999) ? null : (double) r;          // 999 = FIR 전역 → 원 미표시
        }

        Matcher e = E_GEOM.matcher(fullText);
        if (e.find()) {
            radiusNm = (double) Integer.parseInt(e.group(1));
            lat = dms(e.group(2), 2, e.group(3).equals("N"));   // DDMMSS
            lon = dms(e.group(4), 3, e.group(5).equals("E"));   // DDDMMSS
        }

        if (lat == null || lon == null) return Geometry.NONE;
        return new Geometry(lat, lon, radiusNm);
    }

    /**
     * "도분(초)" 좌표 문자열을 10진수 도(degree)로 변환한다.
     * @param s        숫자 문자열 (예: 위도 "3804"/"380412", 경도 "12724"/"1272407")
     * @param degDigits 도(度) 자릿수 — 위도 2, 경도 3. 그 뒤로 분 2자리, (있으면) 초 2자리.
     * @param positive  N/E면 true(+), S/W면 false(−).
     */
    private static double dms(String s, int degDigits, boolean positive) {
        int deg = Integer.parseInt(s.substring(0, degDigits));
        int min = Integer.parseInt(s.substring(degDigits, degDigits + 2));
        int sec = s.length() >= degDigits + 4
                ? Integer.parseInt(s.substring(degDigits + 2, degDigits + 4)) : 0;
        double value = deg + min / 60.0 + sec / 3600.0;
        return positive ? value : -value;
    }

    private static LocalDateTime parseNotamDate(String raw, DateTimeFormatter fmt) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.length() == 10 ? "20" + raw : raw;
        try {
            return LocalDateTime.parse(s, fmt);
        } catch (Exception e) {
            return null;
        }
    }
}
