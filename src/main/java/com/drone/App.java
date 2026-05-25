package com.drone;

import com.drone.view.MainFrame;
import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) {
        // 일부 타일 서버(OSM 등)는 기본/없음 User-Agent를 거부함. 가장 이른 시점에 설정.
        System.setProperty("http.agent", "DroneNow/1.0 (cfi2000@ogqcorp.com)");
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
