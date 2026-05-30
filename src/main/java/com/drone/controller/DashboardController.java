package com.drone.controller;

import com.drone.ConfigLoader;
import com.drone.api.ApiStatus;
import com.drone.api.ApiStatusBus;
import com.drone.api.NotamClient;
import com.drone.api.RainForecastClient;
import com.drone.api.SunTimeClient;
import com.drone.api.WeatherClient;
import com.drone.io.AppDataStore;
import com.drone.io.FileCacheStore;
import com.drone.io.NoFlyZoneStore;
import com.drone.judge.FlightSafetyJudge;
import com.drone.judge.FlightSafetyReport;
import com.drone.model.Location;
import com.drone.model.PilotLicense;
import com.drone.model.dto.NoFlyZoneInfo;
import com.drone.model.dto.NotamInfo;
import com.drone.model.dto.RainForecastInfo;
import com.drone.model.dto.SunTimeInfo;
import com.drone.model.dto.WeatherInfo;
import com.drone.view.ChecklistPanel;
import com.drone.view.ConditionsPanel;
import com.drone.view.NoFlyZonePanel;
import com.drone.view.NotamPanel;

import javax.swing.SwingWorker;
import java.util.List;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public class DashboardController {
    private final WeatherClient weatherClient;
    private final RainForecastClient rainForecastClient;
    private final SunTimeClient sunTimeClient;
    private final NoFlyZoneStore noFlyZoneStore;
    private final NotamClient notamClient;
    private final FlightSafetyJudge judge;
    private final AppDataStore dataStore;
    private final ApiStatusBus statusBus = new ApiStatusBus();

    /** 풋터 상태바가 구독하는 비행금지구역(로컬 번들) 상태 키. */
    public static final String ZONE_KEY = "공역";

    private ConditionsPanel conditionsPanel;
    private NotamPanel notamPanel;
    private NoFlyZonePanel noFlyZonePanel;
    private ChecklistPanel checklistPanel;

    /** 지도 클릭 시 마지막 선택 위치를 외부(예: MainFrame)에 알리는 콜백. */
    private Consumer<Location> locationListener;

    public DashboardController(AppDataStore dataStore, NoFlyZoneStore noFlyZoneStore) {
        this.dataStore = dataStore;
        this.noFlyZoneStore = noFlyZoneStore;
        FileCacheStore cache = new FileCacheStore();
        String dataKey = ConfigLoader.get("api.dataportal.key");
        this.weatherClient = new WeatherClient(cache, dataKey);
        this.rainForecastClient = new RainForecastClient(cache, dataKey);
        this.sunTimeClient = new SunTimeClient(cache, dataKey);
        this.notamClient = new NotamClient(cache);
        this.judge = new FlightSafetyJudge();
        // 각 클라이언트가 캐시/실시간/실패를 풋터 상태바로 보고하도록 연결한다.
        this.weatherClient.setStatusBus(statusBus);
        this.rainForecastClient.setStatusBus(statusBus);
        this.sunTimeClient.setStatusBus(statusBus);
        this.notamClient.setStatusBus(statusBus);
    }

    /** 풋터 상태바가 구독할 데이터 소스 상태 버스. */
    public ApiStatusBus getStatusBus() { return statusBus; }

    public void setConditionsPanel(ConditionsPanel p) { this.conditionsPanel = p; }
    public void setNotamPanel(NotamPanel p) { this.notamPanel = p; }
    public void setNoFlyZonePanel(NoFlyZonePanel p) { this.noFlyZonePanel = p; }
    public void setChecklistPanel(ChecklistPanel p) { this.checklistPanel = p; }
    public void setLocationListener(Consumer<Location> l) { this.locationListener = l; }

    public void onMapClicked(Location loc) {
        if (locationListener != null) locationListener.accept(loc);
        LocalDateTime when = LocalDateTime.now();
        PilotLicense license = dataStore.loadLicense();

        // 클릭 즉시 체크리스트를 회색 "확인 중…" 상태로 전환 → 데이터 도착 순서대로 활성화한다.
        if (checklistPanel != null) checklistPanel.showLoading();

        new SwingWorker<FlightSafetyReport, FlightSafetyReport.ChecklistItem>() {
            WeatherInfo w;
            RainForecastInfo f;
            NotamInfo n;
            NoFlyZoneInfo z;
            SunTimeInfo s;

            @Override
            protected FlightSafetyReport doInBackground() {
                // 로컬 즉시 판정(자격·금지구역)부터 활성화하고, 네트워크 응답은 도착 순서대로 채운다.
                publish(judge.checkLicense(license));
                statusBus.report(ZONE_KEY, ApiStatus.LOADING, "교차 검사 중");
                z = safe(() -> noFlyZoneStore.zonesContaining(loc.lat(), loc.lon()), "비행금지구역");
                statusBus.report(ZONE_KEY, z != null ? ApiStatus.LIVE : ApiStatus.FAILED,
                        z != null ? "로컬 번들 (357 폴리곤)" : "로드 실패");
                publish(judge.checkNoFlyZone(z));
                statusBus.report(weatherClient.name(), ApiStatus.LOADING, "조회 중");
                w = safe(() -> weatherClient.get(loc, when), "기상");
                publish(judge.checkWind(w), judge.checkRain(w));
                statusBus.report(rainForecastClient.name(), ApiStatus.LOADING, "조회 중");
                f = safe(() -> rainForecastClient.get(loc, when), "강수예보");
                statusBus.report(notamClient.name(), ApiStatus.LOADING, "조회 중");
                n = safe(() -> notamClient.get(loc, when), "NOTAM");
                publish(judge.checkNotam(n, loc));
                statusBus.report(sunTimeClient.name(), ApiStatus.LOADING, "조회 중");
                s = safe(() -> sunTimeClient.get(loc, when), "일출일몰");
                publish(judge.checkDaylight(s, when));
                return judge.judge(license, loc, when, w, n, z, s);
            }

            @Override
            protected void process(List<FlightSafetyReport.ChecklistItem> chunks) {
                if (checklistPanel != null) chunks.forEach(checklistPanel::setItemResult);
            }

            @Override
            protected void done() {
                // 개별 API 결과(null=실패)는 항상 패널에 반영해 빈 화면을 방지한다.
                if (conditionsPanel != null) conditionsPanel.update(w, f, s);
                // NOTAM 패널이 필터를 거쳐 지도(setOnFilterChange 콜백)까지 갱신한다.
                if (notamPanel != null) notamPanel.update(n);
                if (noFlyZonePanel != null) noFlyZonePanel.update(z);
                FlightSafetyReport report = null;
                try {
                    report = get();
                } catch (Exception ex) {
                    System.err.println("Dashboard 판정 실패: " + ex.getMessage());
                }
                if (checklistPanel != null) checklistPanel.update(report);
            }
        }.execute();
    }

    private static <T> T safe(java.util.concurrent.Callable<T> c, String label) {
        try {
            return c.call();
        } catch (Exception e) {
            System.err.println("[" + label + "] 호출 실패: " + e.getMessage());
            return null;
        }
    }
}
