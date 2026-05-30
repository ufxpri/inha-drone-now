package com.drone.view;

import com.drone.api.ApiStatusBus;
import com.drone.api.ApiStatusBus.Entry;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 창 가장 아래 풋터(VS Code 풍 상태바). 데이터 소스별 마지막 조회 상태를
 * 색점 + 이름 + 상태(실시간/캐시/실패/대기)로 표시하고, 툴팁에 상세·갱신 시각을 보여준다.
 *
 * <p>{@link ApiStatusBus}를 구독해 변경된 칩만 EDT에서 갱신한다.
 */
public class StatusBarPanel extends JPanel {
    private static final Color BG = new Color(37, 37, 38);
    private static final Color FG = new Color(200, 200, 200);
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ApiStatusBus bus;
    private final Map<String, JLabel> chips = new LinkedHashMap<>();

    public StatusBarPanel(ApiStatusBus bus, String... keys) {
        this.bus = bus;
        setLayout(new FlowLayout(FlowLayout.LEFT, 16, 3));
        setBackground(BG);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(70, 70, 75)));

        for (String key : keys) {
            JLabel chip = new JLabel();
            chip.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
            chip.setForeground(FG);
            chips.put(key, chip);
            add(chip);
            refresh(key);
        }
        // 워커 스레드에서 오는 통지를 EDT로 옮겨 해당 칩만 갱신한다.
        bus.addListener(key -> SwingUtilities.invokeLater(() -> refresh(key)));
    }

    private void refresh(String key) {
        JLabel chip = chips.get(key);
        if (chip == null) return;
        Entry e = bus.get(key);
        chip.setText("<html><font color='" + hex(e.status().color) + "'>●</font> "
                + "<b>" + key + "</b> <font color='#9a9a9a'>" + e.status().label + "</font></html>");
        String when = e.at() == null ? "" : "  ·  " + e.at().format(HMS);
        chip.setToolTipText("<html>" + key + " — " + e.status().label
                + (e.detail() == null ? "" : "<br/>" + escape(e.detail())) + when + "</html>");
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escape(String s) {
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }
}
