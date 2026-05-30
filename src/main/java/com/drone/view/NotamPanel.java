package com.drone.view;

import com.drone.model.NotamCategory;
import com.drone.model.dto.NotamInfo;
import com.drone.model.dto.NotamInfo.NotamItem;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NotamPanel extends JPanel {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final DefaultListModel<NotamItem> model = new DefaultListModel<>();
    private final JList<NotamItem> list = new JList<>(model);
    private final JLabel header = new JLabel("NOTAM");

    /** 마지막으로 수신한 전체 NOTAM(필터 전 원본). */
    private List<NotamItem> all = List.of();
    /** 현재 표시할 경보 종류(체크된 것). 기본 전체 선택. */
    private final EnumSet<NotamCategory> active = EnumSet.allOf(NotamCategory.class);
    private final Map<NotamCategory, JCheckBox> checkBoxes = new EnumMap<>(NotamCategory.class);

    /** 필터 결과(표시 대상)를 외부(지도)에 전달하는 콜백. */
    private Consumer<List<NotamItem>> onFilterChange;

    public NotamPanel() {
        setLayout(new BorderLayout(6, 6));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        header.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        north.add(header, BorderLayout.NORTH);
        north.add(buildFilterBar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        list.setBackground(Color.WHITE);
        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> renderItem(value, isSelected));
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        add(sp, BorderLayout.CENTER);

        showPlaceholder();
    }

    /** 필터 결과 콜백 등록(지도 갱신용). */
    public void setOnFilterChange(Consumer<List<NotamItem>> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    /**
     * 경보 종류별 체크박스 필터. 2열 그리드라 카드가 좁아져도 줄바꿈이 정상 표시되고,
     * (FlowLayout과 달리) 선호 폭이 작아 카드 폭 가중치를 방해하지 않는다.
     */
    private JComponent buildFilterBar() {
        JPanel bar = new JPanel(new GridLayout(0, 2, 4, 0));
        bar.setOpaque(false);
        for (NotamCategory cat : NotamCategory.values()) {
            JCheckBox cb = new JCheckBox(cat.label, true);
            cb.setOpaque(false);
            cb.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
            cb.setForeground(cat.color);
            cb.setFocusPainted(false);
            cb.setMargin(new Insets(0, 0, 0, 0));
            cb.addActionListener(e -> {
                if (cb.isSelected()) active.add(cat); else active.remove(cat);
                applyFilter();
            });
            checkBoxes.put(cat, cb);
            bar.add(cb);
        }
        return bar;
    }

    private JComponent renderItem(NotamItem value, boolean selected) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(selected ? new Color(225, 235, 255) : Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 235, 235)),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));

        NotamCategory cat = value.category();
        JLabel badge = new JLabel("●");
        badge.setForeground(cat.color);
        badge.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        badge.setToolTipText(cat.label);
        row.add(badge, BorderLayout.WEST);

        String period = "";
        if (value.from() != null && value.to() != null) {
            period = value.from().format(FMT) + " ~ " + value.to().format(FMT);
        }
        String tag = "<span style='color:" + toHex(cat.color) + "'>[" + cat.shortLabel + "]</span> ";
        JLabel lbl = new JLabel("<html>" + tag + "<b>" + esc(value.area()) + "</b><br/>"
                + esc(value.content())
                + (period.isEmpty() ? "" : "<br/><span style='color:#888;font-size:9px'>" + period + "</span>")
                + "</html>");
        lbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        row.add(lbl, BorderLayout.CENTER);
        return row;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showPlaceholder() {
        all = List.of();
        model.clear();
        header.setText("NOTAM — 지도를 클릭하세요");
        header.setForeground(Color.GRAY);
    }

    public void update(NotamInfo n) {
        if (n == null) {
            showError();
            return;
        }
        all = n.items() == null ? List.of() : new ArrayList<>(n.items());
        applyFilter();
    }

    public void showError() {
        all = List.of();
        model.clear();
        header.setForeground(new Color(207, 34, 46));
        header.setText("NOTAM — 불러오기 실패");
        if (onFilterChange != null) onFilterChange.accept(List.of());
    }

    /** active 집합에 따라 목록을 다시 채우고, 지도에도 같은 결과를 밀어준다. */
    private void applyFilter() {
        // 종류별 개수를 체크박스 라벨에 반영
        Map<NotamCategory, Long> counts = all.stream()
                .collect(Collectors.groupingBy(NotamItem::category, Collectors.counting()));
        for (NotamCategory cat : NotamCategory.values()) {
            JCheckBox cb = checkBoxes.get(cat);
            if (cb != null) cb.setText(cat.label + " (" + counts.getOrDefault(cat, 0L) + ")");
        }

        List<NotamItem> shown = all.stream()
                .filter(it -> active.contains(it.category()))
                .collect(Collectors.toList());

        model.clear();
        shown.forEach(model::addElement);
        header.setForeground(Color.DARK_GRAY);
        header.setText("NOTAM — " + shown.size() + " / " + all.size() + "건");

        if (onFilterChange != null) onFilterChange.accept(shown);
    }
}
