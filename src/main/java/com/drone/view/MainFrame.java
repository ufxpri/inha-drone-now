package com.drone.view;

import com.drone.controller.DashboardController;
import com.drone.controller.SpotController;
import com.drone.io.AppDataStore;
import com.drone.io.NoFlyZoneStore;
import com.drone.model.Location;
import com.drone.model.PilotLicense;

import javax.swing.*;
import java.awt.*;

/**
 * VS Code 풍의 단일 화면 레이아웃:
 *   NORTH : 타이틀 바 (앱 이름 + 자격 콤보 + 판정 배너)
 *   WEST  : 액티비티 바(아이콘 토글)
 *   CENTER: (좌)관심지점 사이드바 | 지도 | (우)체크리스트  위에  하단 카드(강수·풍속·일출·기온·금지구역·NOTAM)
 *   SOUTH : 데이터 소스 상태바(풋터)
 * 좌/중/우와 상/하는 모두 JSplitPane으로 크기 조절 가능.
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
        NoFlyZoneStore noFlyZoneStore = new NoFlyZoneStore();
        DashboardController dashboard = new DashboardController(dataStore, noFlyZoneStore);
        SpotController spotController = new SpotController(dataStore);

        // ===== 상단 타이틀 바 =====
        JLabel verdictBanner = buildVerdictBanner();
        JPanel top = buildTopBar(dataStore, verdictBanner);
        add(top, BorderLayout.NORTH);

        // ===== 각 패널 생성 =====
        MapPanel mapPanel = new MapPanel(dashboard);
        // 번들 공역 폴리곤을 시작 시 한 번 지도에 표시(클릭 전부터 보이도록)
        mapPanel.setNoFlyPolygons(noFlyZoneStore.all());
        ChecklistPanel checklistPanel = new ChecklistPanel(verdictBanner);
        ConditionsPanel conditionsPanel = new ConditionsPanel();
        NotamPanel notamPanel = new NotamPanel();
        NoFlyZonePanel noFlyZonePanel = new NoFlyZonePanel();
        // NOTAM 종류 필터 결과를 지도 표시(원·라벨)에도 그대로 반영한다.
        notamPanel.setOnFilterChange(mapPanel::setNotams);

        // 지도 클릭 시 현재 위치 저장(관심지점 추가에 사용)
        dashboard.setLocationListener(loc -> this.currentLocation = loc);
        // 관심지점: 추가는 현재 클릭 위치를 사용, 더블클릭 시 해당 위치로 대시보드 재실행
        SpotListPanel spotListPanel = new SpotListPanel(
                spotController,
                () -> this.currentLocation,
                loc -> dashboard.onMapClicked(loc));

        dashboard.setChecklistPanel(checklistPanel);
        dashboard.setConditionsPanel(conditionsPanel);
        dashboard.setNotamPanel(notamPanel);
        dashboard.setNoFlyZonePanel(noFlyZonePanel);

        // ===== 좌측 사이드바 (관심지점) =====
        leftSidebar = buildSidebar("관심지점", spotListPanel);
        leftSidebar.setPreferredSize(new Dimension(220, 0));

        // ===== 우측 패널 (체크리스트) =====
        rightPanel = buildRightPanel(checklistPanel);
        rightPanel.setPreferredSize(new Dimension(320, 0));

        // ===== 하단 패널 (기상&일출일몰 | 비행금지구역 | NOTAM 가로 3열) =====
        bottomPanel = buildBottomPanel(conditionsPanel, noFlyZonePanel, notamPanel);
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

        // ===== 풋터 상태바: 데이터 소스별 연결/캐시 상태 =====
        StatusBarPanel statusBar = new StatusBarPanel(dashboard.getStatusBus(),
                "기상청", "강수예보", "일출일몰", "NOTAM", DashboardController.ZONE_KEY);

        add(activityBar, BorderLayout.WEST);
        add(verticalSplit, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel buildTopBar(AppDataStore dataStore, JLabel verdictBanner) {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(TITLEBAR_BG);
        top.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JLabel title = new JLabel("드론 지금 당장");
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

        JToggleButton spotsBtn = makeActivityButton("★", "관심지점", true);
        JToggleButton notamBtn = makeActivityButton("☰", "NOTAM/체크리스트", true);
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
        // Dialog(논리 폰트)는 ★ ☰ ⚙ 등 기호를 한글과 함께 폴백 렌더링한다(이모지 누락 박스 방지).
        b.setFont(new Font("Dialog", Font.PLAIN, 18));
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

    /**
     * 패널 상단에 붙는 회색 섹션 헤더 라벨(좌측 여백 + 하단 1px 구분선).
     * 사이드바·체크리스트·하단 카드가 모두 이 한 가지 스타일을 공유한다.
     */
    private JLabel sectionHeader(String text, Font font, int padV) {
        JLabel header = new JLabel("  " + text);
        header.setOpaque(true);
        header.setBackground(new Color(230, 230, 230));
        header.setFont(font);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(padV, 4, padV, 4)));
        return header;
    }

    private JPanel buildSidebar(String title, JComponent body) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(sectionHeader(title.toUpperCase(), new Font("Malgun Gothic", Font.BOLD, 11), 6), BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Color.GRAY));
        return p;
    }

    /** 우측 패널: 체크리스트(+판정 배너는 상단 타이틀바 공유). */
    private JPanel buildRightPanel(ChecklistPanel checklist) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY));
        panel.add(sectionHeader("✓  체크리스트", new Font("Dialog", Font.BOLD, 12), 6), BorderLayout.NORTH);
        panel.add(checklist, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 하단 패널: 치명 3요소(강수확률·풍속풍향·일출일몰)를 비행금지·NOTAM과 같은 최상위 카드로 격상.
     * 모든 카드는 동일한 회색 헤더 스타일이며, 폭만 가중치로 배분한다(기온은 참고용이라 좁게).
     */
    private JPanel buildBottomPanel(ConditionsPanel conditions,
                                    NoFlyZonePanel noFly, NotamPanel notam) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(Color.GRAY);
        int gx = 0;
        addColumn(grid, gx++, 3, titledColumn("☂  강수확률", conditions.rainVisual()));
        addColumn(grid, gx++, 1, titledColumn("➤  풍속·풍향", conditions.windVisual()));
        addColumn(grid, gx++, 3, titledColumn("☀  일출·일몰", conditions.sunVisual()));
        addColumn(grid, gx++, 1, titledColumn("℃  기온", conditions.tempVisual()));
        addColumn(grid, gx++, 3, titledColumn("⛔  비행금지구역", noFly));
        addColumn(grid, gx, 5, titledColumn("⚑  NOTAM", notam));

        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 가중 폭의 하단 카드 한 칸을 grid에 추가한다(칸 사이 1px 회색 거터).
     * 카드의 선호/최소 폭을 0에 가깝게 눌러, 폭이 오직 weightx 비율로만 결정되게 한다.
     * (그렇지 않으면 NOTAM 필터바·리스트 같은 내부 요소의 큰 선호 폭이 비율을 망가뜨린다.)
     */
    private void addColumn(JPanel grid, int gridx, double weightx, JComponent col) {
        col.setMinimumSize(new Dimension(0, 0));
        col.setPreferredSize(new Dimension(10, 10));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = gridx;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = weightx;
        gc.weighty = 1.0;
        gc.insets = new Insets(0, gridx == 0 ? 0 : 1, 0, 0);
        grid.add(col, gc);
    }

    /** 하단 카드에 회색 헤더를 붙인다. (Dialog 폰트로 선두 기호 ☂ ➤ ☀ ℃ ⛔ ⚑ 를 안정적으로 렌더링) */
    private JPanel titledColumn(String title, JComponent body) {
        JPanel col = new JPanel(new BorderLayout());
        col.setBackground(Color.WHITE);
        col.add(sectionHeader(title, new Font("Dialog", Font.BOLD, 12), 4), BorderLayout.NORTH);
        col.add(body, BorderLayout.CENTER);
        return col;
    }
}
