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
            int latDeg = Integer.parseInt(q.group(1).substring(0, 2));
            int latMin = Integer.parseInt(q.group(1).substring(2, 4));
            lat = (latDeg + latMin / 60.0) * (q.group(2).equals("N") ? 1 : -1);
            int lonDeg = Integer.parseInt(q.group(3).substring(0, 3));
            int lonMin = Integer.parseInt(q.group(3).substring(3, 5));
            lon = (lonDeg + lonMin / 60.0) * (q.group(4).equals("E") ? 1 : -1);
            int r = Integer.parseInt(q.group(5));
            radiusNm = (r == 999) ? null : (double) r;  // 999 = FIR 전역 → 원 미표시
        }

        Matcher e = E_GEOM.matcher(fullText);
        if (e.find()) {
            radiusNm = (double) Integer.parseInt(e.group(1));
            String latS = e.group(2); // DDMMSS
            int latDeg = Integer.parseInt(latS.substring(0, 2));
            int latMin = Integer.parseInt(latS.substring(2, 4));
            int latSec = Integer.parseInt(latS.substring(4, 6));
            lat = (latDeg + latMin / 60.0 + latSec / 3600.0) * (e.group(3).equals("N") ? 1 : -1);
            String lonS = e.group(4); // DDDMMSS
            int lonDeg = Integer.parseInt(lonS.substring(0, 3));
            int lonMin = Integer.parseInt(lonS.substring(3, 5));
            int lonSec = Integer.parseInt(lonS.substring(5, 7));
            lon = (lonDeg + lonMin / 60.0 + lonSec / 3600.0) * (e.group(5).equals("E") ? 1 : -1);
        }

        if (lat == null || lon == null) return Geometry.NONE;
        return new Geometry(lat, lon, radiusNm);
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
