package com.drone.view;

import com.drone.judge.FlightSafetyReport;
import com.drone.judge.FlightSafetyReport.ChecklistItem;
import com.drone.judge.FlightSafetyReport.Verdict;

import javax.swing.*;
import java.awt.*;

public class ChecklistPanel extends JPanel {
    private static final Color GREEN = new Color(46, 160, 67);
    private static final Color ORANGE = new Color(232, 142, 0);
    private static final Color RED = new Color(207, 34, 46);

    private final DefaultListModel<ChecklistItem> model = new DefaultListModel<>();
    private final JList<ChecklistItem> list = new JList<>(model);
    private final JLabel verdictBanner;
    private final boolean ownsBanner;
    private final JLabel hint = new JLabel("지도를 클릭하면 비행 안전 판정을 표시합니다.", SwingConstants.CENTER);

    /** 기본 생성자: 내부에 자체 verdict 배너를 표시. */
    public ChecklistPanel() {
        this(null);
    }

    /**
     * 외부에서 전달된 verdict 라벨을 사용하는 생성자(상단 타이틀바에 배치할 때 사용).
     * externalVerdictLabel 이 null 이면 내부 배너를 표시.
     */
    public ChecklistPanel(JLabel externalVerdictLabel) {
        setLayout(new BorderLayout(8, 8));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (externalVerdictLabel == null) {
            verdictBanner = new JLabel(" ", SwingConstants.CENTER);
            verdictBanner.setFont(new Font("Malgun Gothic", Font.BOLD, 24));
            verdictBanner.setOpaque(true);
            verdictBanner.setPreferredSize(new Dimension(0, 60));
            verdictBanner.setBackground(Color.LIGHT_GRAY);
            verdictBanner.setForeground(Color.DARK_GRAY);
            verdictBanner.setText("판정 대기");
            ownsBanner = true;
            add(verdictBanner, BorderLayout.NORTH);
        } else {
            verdictBanner = externalVerdictLabel;
            ownsBanner = false;
        }

        hint.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        hint.setForeground(Color.GRAY);

        list.setBackground(Color.WHITE);
        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> renderItem(value));

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Color.WHITE);
        center.add(hint, BorderLayout.NORTH);
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        showPlaceholder();
    }

    private JComponent renderItem(ChecklistItem value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 235, 235)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        row.add(new StatusDot(colorFor(value.status())), BorderLayout.WEST);
        JLabel lbl = new JLabel("<html><b>" + value.label() + "</b> — " + value.status()
                + "<br/><span style='color:#666'>" + value.detail() + "</span></html>");
        lbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        row.add(lbl, BorderLayout.CENTER);
        return row;
    }

    private void showPlaceholder() {
        model.clear();
        hint.setText("지도를 클릭하면 비행 안전 판정을 표시합니다.");
        hint.setForeground(Color.GRAY);
        if (ownsBanner) {
            verdictBanner.setText("판정 대기");
            verdictBanner.setBackground(Color.LIGHT_GRAY);
            verdictBanner.setForeground(Color.DARK_GRAY);
        }
    }

    public void update(FlightSafetyReport report) {
        model.clear();
        if (report == null) {
            hint.setText("불러오기 실패");
            hint.setForeground(RED);
            verdictBanner.setText("판정 실패");
            verdictBanner.setBackground(Color.LIGHT_GRAY);
            verdictBanner.setForeground(Color.DARK_GRAY);
            return;
        }
        hint.setText(" ");
        report.items().forEach(model::addElement);
        Verdict v = report.overall();
        verdictBanner.setText(switch (v) {
            case GO -> "GO — 비행 가능";
            case CAUTION -> "CAUTION — 주의";
            case NO_GO -> "NO-GO — 비행 불가";
        });
        verdictBanner.setBackground(colorFor(v));
        verdictBanner.setForeground(Color.WHITE);
        if (!ownsBanner) verdictBanner.setOpaque(true);
    }

    public void showError() {
        this.update((FlightSafetyReport) null);
    }

    private static Color colorFor(Verdict v) {
        return switch (v) {
            case GO -> GREEN;
            case CAUTION -> ORANGE;
            case NO_GO -> RED;
        };
    }

    /** 색상 상태 점(●). */
    static class StatusDot extends JComponent {
        private final Color color;
        StatusDot(Color c) { this.color = c; setPreferredSize(new Dimension(16, 16)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = 12, x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
            g2.setColor(color);
            g2.fillOval(x, y, d, d);
            g2.dispose();
        }
    }
}
