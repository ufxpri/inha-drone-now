package com.drone.controller;

import com.drone.ConfigLoader;
import com.drone.api.NotamClient;
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
import com.drone.model.dto.SunTimeInfo;
import com.drone.model.dto.WeatherInfo;
import com.drone.view.ChecklistPanel;
import com.drone.view.MapPanel;
import com.drone.view.NoFlyZonePanel;
import com.drone.view.NotamPanel;
import com.drone.view.SunTimePanel;
import com.drone.view.WeatherPanel;

import javax.swing.SwingWorker;
import java.util.List;
import java.time.LocalDateTime;
import java.util.function.Consumer;

public class DashboardController {
    private final WeatherClient weatherClient;
    private final SunTimeClient sunTimeClient;
    private final NoFlyZoneStore noFlyZoneStore;
    private final NotamClient notamClient;
    private final FlightSafetyJudge judge;
    private final AppDataStore dataStore;

    private WeatherPanel weatherPanel;
    private NotamPanel notamPanel;
    private NoFlyZonePanel noFlyZonePanel;
    private SunTimePanel sunTimePanel;
    private ChecklistPanel checklistPanel;
    private MapPanel mapPanel;

    /** 지도 클릭 시 마지막 선택 위치를 외부(예: MainFrame)에 알리는 콜백. */
    private Consumer<Location> locationListener;

    public DashboardController(AppDataStore dataStore, NoFlyZoneStore noFlyZoneStore) {
        this.dataStore = dataStore;
        this.noFlyZoneStore = noFlyZoneStore;
        FileCacheStore cache = new FileCacheStore();
        String dataKey = ConfigLoader.get("api.dataportal.key");
        this.weatherClient = new WeatherClient(cache, dataKey);
        this.sunTimeClient = new SunTimeClient(cache, dataKey);
        this.notamClient = new NotamClient(cache);
        this.judge = new FlightSafetyJudge();
    }

    public void setWeatherPanel(WeatherPanel p) { this.weatherPanel = p; }
    public void setNotamPanel(NotamPanel p) { this.notamPanel = p; }
    public void setNoFlyZonePanel(NoFlyZonePanel p) { this.noFlyZonePanel = p; }
    public void setSunTimePanel(SunTimePanel p) { this.sunTimePanel = p; }
    public void setChecklistPanel(ChecklistPanel p) { this.checklistPanel = p; }
    public void setMapPanel(MapPanel p) { this.mapPanel = p; }
    public void setLocationListener(Consumer<Location> l) { this.locationListener = l; }

    public void onMapClicked(Location loc) {
        if (locationListener != null) locationListener.accept(loc);
        LocalDateTime when = LocalDateTime.now();
        PilotLicense license = dataStore.loadLicense();

        new SwingWorker<FlightSafetyReport, Void>() {
            WeatherInfo w;
            NotamInfo n;
            NoFlyZoneInfo z;
            SunTimeInfo s;

            @Override
            protected FlightSafetyReport doInBackground() {
                w = safe(() -> weatherClient.get(loc, when), "기상");
                n = safe(() -> notamClient.get(loc, when), "NOTAM");
                z = safe(() -> noFlyZoneStore.zonesContaining(loc.lat(), loc.lon()), "비행금지구역");
                s = safe(() -> sunTimeClient.get(loc, when), "일출일몰");
                return judge.judge(license, loc, when, w, n, z, s);
            }

            @Override
            protected void done() {
                // 개별 API 결과(null=실패)는 항상 패널에 반영해 빈 화면을 방지한다.
                if (weatherPanel != null) weatherPanel.update(w);
                if (notamPanel != null) notamPanel.update(n);
                if (mapPanel != null) {
                    mapPanel.setNotams(n != null && n.items() != null ? n.items() : List.of());
                }
                if (noFlyZonePanel != null) noFlyZonePanel.update(z);
                if (sunTimePanel != null) sunTimePanel.update(s);
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
