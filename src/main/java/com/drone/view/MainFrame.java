package com.drone.view;

import com.drone.controller.DashboardController;
import com.drone.controller.SpotController;
import com.drone.io.AppDataStore;
import com.drone.model.Location;
import com.drone.model.PilotLicense;

import javax.swing.*;
import java.awt.*;

/**
 * VS Code 풍의 단일 화면 레이아웃:
 *   NORTH: 타이틀 바 (앱 이름 + 자격 콤보 + 판정 배너)
 *   WEST : 액티비티 바(아이콘 토글) + 좌측 사이드바(관심지점)
 *   CENTER: 지도
 *   EAST : 우측 정보 패널 스택(기상/일출일몰/비행금지구역)  -- Collapsible 섹션
 *   SOUTH: 하단 패널(체크리스트 + NOTAM 가로 분할)
 */
public class MainFrame extends JFrame {

    private static final Color ACTIVITY_BG = new Color(45, 45, 48);
    private static final Color ACTIVITY_FG = new Color(220, 220, 220);
    private static final Color TITLEBAR_BG = new Color(60, 63, 65);
    private static final Color TITLEBAR_FG = new Color(240, 240, 240);

    private JPanel leftSidebar;
    private JPanel rightPanel;
    private JPanel bottomPanel;
    private JSplitPane centerHSplit;   // sidebar | map
    private JSplitPane mapEastSplit;   // (sidebar|map) | right
    private JSplitPane verticalSplit;  // top | bottom

    /** 지도에서 마지막으로 클릭된 위치(관심지점 추가에 사용). */
    private Location currentLocation;

    public MainFrame() {
        super("드론 지금 당장");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1400, 880);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        AppDataStore dataStore = new AppDataStore();
        DashboardController dashboard = new DashboardController(dataStore);
        SpotController spotController = new SpotController(dataStore);

        // ===== 상단 타이틀 바 =====
        JLabel verdictBanner = buildVerdictBanner();
        JPanel top = buildTopBar(dataStore, verdictBanner);
        add(top, BorderLayout.NORTH);

        // ===== 각 패널 생성 =====
        MapPanel mapPanel = new MapPanel(dashboard);
        ChecklistPanel checklistPanel = new ChecklistPanel(verdictBanner);
        WeatherPanel weatherPanel = new WeatherPanel();
        NotamPanel notamPanel = new NotamPanel();
        NoFlyZonePanel noFlyZonePanel = new NoFlyZonePanel();
        SunTimePanel sunTimePanel = new SunTimePanel();

        // 지도 클릭 시 현재 위치 저장(관심지점 추가에 사용)
        dashboard.setLocationListener(loc -> this.currentLocation = loc);
        // 관심지점: 추가는 현재 클릭 위치를 사용, 더블클릭 시 해당 위치로 대시보드 재실행
        SpotListPanel spotListPanel = new SpotListPanel(
                spotController,
                () -> this.currentLocation,
                loc -> dashboard.onMapClicked(loc));

        dashboard.setChecklistPanel(checklistPanel);
        dashboard.setWeatherPanel(weatherPanel);
        dashboard.setNotamPanel(notamPanel);
        dashboard.setNoFlyZonePanel(noFlyZonePanel);
        dashboard.setSunTimePanel(sunTimePanel);
        dashboard.setMapPanel(mapPanel);

        // ===== 좌측 사이드바 (관심지점) =====
        leftSidebar = buildSidebar("관심지점", spotListPanel);
        leftSidebar.setPreferredSize(new Dimension(220, 0));

        // ===== 우측 패널 (체크리스트) =====
        rightPanel = buildRightPanel(checklistPanel);
        rightPanel.setPreferredSize(new Dimension(320, 0));

        // ===== 하단 패널 (기상 | 일출일몰 | 비행금지구역 | NOTAM 가로 4열) =====
        bottomPanel = buildBottomPanel(weatherPanel, sunTimePanel, noFlyZonePanel, notamPanel);
        bottomPanel.setPreferredSize(new Dimension(0, 240));

        // ===== 중앙 영역 split: (사이드바|지도) | 우측 =====
        centerHSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSidebar, mapPanel);
        centerHSplit.setDividerLocation(220);
        centerHSplit.setBorder(null);
        centerHSplit.setContinuousLayout(true);

        mapEastSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerHSplit, rightPanel);
        mapEastSplit.setResizeWeight(1.0);
        mapEastSplit.setDividerLocation(860);
        mapEastSplit.setBorder(null);
        mapEastSplit.setContinuousLayout(true);

        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapEastSplit, bottomPanel);
        verticalSplit.setResizeWeight(1.0);
        verticalSplit.setDividerLocation(540);
        verticalSplit.setBorder(null);
        verticalSplit.setContinuousLayout(true);

        // ===== 액티비티 바 =====
        JPanel activityBar = buildActivityBar();

        add(activityBar, BorderLayout.WEST);
        add(verticalSplit, BorderLayout.CENTER);
    }

    private JPanel buildTopBar(AppDataStore dataStore, JLabel verdictBanner) {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(TITLEBAR_BG);
        top.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel title = new JLabel("🛩  드론 지금 당장");
        title.setForeground(TITLEBAR_FG);
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        top.add(title, BorderLayout.WEST);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        center.setOpaque(false);
        JLabel licLbl = new JLabel("조종 자격:");
        licLbl.setForeground(TITLEBAR_FG);
        center.add(licLbl);
        JComboBox<PilotLicense> licenseBox = new JComboBox<>(PilotLicense.values());
        licenseBox.setSelectedItem(dataStore.loadLicense());
        licenseBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = new JLabel(value == null ? "" : value.getDisplayName() + " — " + value.getAllowedDroneSpec());
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            if (isSelected) lbl.setBackground(new Color(200, 220, 255));
            return lbl;
        });
        licenseBox.addActionListener(e -> {
            PilotLicense sel = (PilotLicense) licenseBox.getSelectedItem();
            if (sel != null) dataStore.saveLicense(sel);
        });
        center.add(licenseBox);
        top.add(center, BorderLayout.CENTER);

        top.add(verdictBanner, BorderLayout.EAST);
        return top;
    }

    private JLabel buildVerdictBanner() {
        JLabel banner = new JLabel("판정 대기", SwingConstants.CENTER);
        banner.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        banner.setOpaque(true);
        banner.setBackground(Color.LIGHT_GRAY);
        banner.setForeground(Color.DARK_GRAY);
        banner.setPreferredSize(new Dimension(220, 36));
        banner.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return banner;
    }

    private JPanel buildActivityBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ACTIVITY_BG);
        bar.setPreferredSize(new Dimension(50, 0));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY));

        JToggleButton spotsBtn = makeActivityButton("📍", "관심지점", true);
        JToggleButton notamBtn = makeActivityButton("🔔", "NOTAM/체크리스트", true);
        JToggleButton settingsBtn = makeActivityButton("⚙", "설정", false);

        spotsBtn.addActionListener(e -> {
            boolean show = spotsBtn.isSelected();
            leftSidebar.setVisible(show);
            if (show) centerHSplit.setDividerLocation(220);
            centerHSplit.revalidate();
            centerHSplit.repaint();
        });
        notamBtn.addActionListener(e -> {
            boolean show = notamBtn.isSelected();
            bottomPanel.setVisible(show);
            if (show) verticalSplit.setDividerLocation(getHeight() - 300);
            verticalSplit.revalidate();
            verticalSplit.repaint();
        });

        bar.add(Box.createVerticalStrut(8));
        bar.add(spotsBtn);
        bar.add(Box.createVerticalStrut(4));
        bar.add(notamBtn);
        bar.add(Box.createVerticalStrut(4));
        bar.add(settingsBtn);
        bar.add(Box.createVerticalGlue());
        return bar;
    }

    private JToggleButton makeActivityButton(String icon, String tooltip, boolean selected) {
        JToggleButton b = new JToggleButton(icon);
        b.setSelected(selected);
        b.setToolTipText(tooltip);
        b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        b.setForeground(ACTIVITY_FG);
        b.setBackground(ACTIVITY_BG);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(50, 44));
        b.setPreferredSize(new Dimension(50, 44));
        b.addChangeListener(e -> b.setBackground(b.isSelected() ? new Color(70, 70, 75) : ACTIVITY_BG));
        return b;
    }

    private JPanel buildSidebar(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel header = new JLabel("  " + title.toUpperCase());
        header.setOpaque(true);
        header.setBackground(new Color(230, 230, 230));
        header.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
        p.add(header, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY));
        return p;
    }

    /** 우측 패널: 체크리스트(+판정 배너는 상단 타이틀바 공유). */
    private JPanel buildRightPanel(ChecklistPanel checklist) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY));

        JLabel header = new JLabel("  ✅  체크리스트");
        header.setOpaque(true);
        header.setBackground(new Color(230, 230, 230));
        header.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
        panel.add(header, BorderLayout.NORTH);
        panel.add(checklist, BorderLayout.CENTER);
        return panel;
    }

    /** 하단 패널: 기상 | 일출일몰 | 비행금지구역 | NOTAM 4열 가로 배치. */
    private JPanel buildBottomPanel(WeatherPanel weather, SunTimePanel sun,
                                    NoFlyZonePanel noFly, NotamPanel notam) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));

        JPanel grid = new JPanel(new GridLayout(1, 4, 1, 0));
        grid.setBackground(Color.GRAY);
        grid.add(titledColumn("🌤  기상 정보", weather));
        grid.add(titledColumn("☀  일출/일몰", sun));
        grid.add(titledColumn("🚫  비행금지구역", noFly));
        grid.add(titledColumn("🔔  NOTAM", notam));
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    /** 하단 4열 각각에 회색 헤더를 붙인다. */
    private JPanel titledColumn(String title, JComponent body) {
        JPanel col = new JPanel(new BorderLayout());
        col.setBackground(Color.WHITE);
        JLabel header = new JLabel("  " + title);
        header.setOpaque(true);
        header.setBackground(new Color(230, 230, 230));
        header.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        col.add(header, BorderLayout.NORTH);
        col.add(body, BorderLayout.CENTER);
        return col;
    }
}
