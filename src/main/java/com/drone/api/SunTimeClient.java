package com.drone.api;

import com.drone.io.FileCacheStore;
import com.drone.model.Location;
import com.drone.model.dto.SunTimeInfo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SunTimeClient extends CachedApiClient<SunTimeInfo> {
    private static final String ENDPOINT =
            "https://apis.data.go.kr/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final Pattern SUNRISE = Pattern.compile("<sunrise>\\s*(\\d{4})\\s*</sunrise>");
    private static final Pattern SUNSET = Pattern.compile("<sunset>\\s*(\\d{4})\\s*</sunset>");

    private final String serviceKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public SunTimeClient(FileCacheStore cache, String serviceKey) {
        super(cache);
        this.serviceKey = serviceKey;
    }

    @Override public String name() { return "일출일몰"; }
    @Override public Duration ttl() { return Duration.ofHours(24); }
    @Override protected Class<SunTimeInfo> type() { return SunTimeInfo.class; }

    @Override
    public String cacheKey(Location loc, LocalDateTime time) {
        return "sun_" + time.toLocalDate() + "_" + loc.lat() + "_" + loc.lon();
    }

    @Override
    public SunTimeInfo fetch(Location loc, LocalDateTime time) throws Exception {
        String date = time.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String url = ENDPOINT + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&locdate=" + date
                + "&longitude=" + loc.lon()
                + "&latitude=" + loc.lat()
                + "&dnYn=Y";

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = resp.body();

        LocalTime sunrise = parseTime(SUNRISE.matcher(body));
        LocalTime sunset = parseTime(SUNSET.matcher(body));
        if (sunrise == null) sunrise = LocalTime.of(6, 0);
        if (sunset == null) sunset = LocalTime.of(18, 0);
        return new SunTimeInfo(sunrise, sunset);
    }

    private static LocalTime parseTime(Matcher m) {
        if (m.find()) {
            String s = m.group(1);
            try {
                return LocalTime.of(Integer.parseInt(s.substring(0, 2)), Integer.parseInt(s.substring(2, 4)));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
