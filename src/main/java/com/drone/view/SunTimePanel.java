package com.drone.view;

import com.drone.model.dto.SunTimeInfo;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;

/**
 * 일출/일몰 패널. 0~24시 가로 타임라인에 낮(노란 호)/밤(회색)을 칠하고
 * 현재 시각을 세로선으로 표시한다.
 */
public class SunTimePanel extends JPanel {
    private static final Font LABEL_FONT = new Font("Malgun Gothic", Font.BOLD, 12);

    private final DayTimeline timeline = new DayTimeline();
    private final JLabel labels = new JLabel();

    public SunTimePanel() {
        setLayout(new BorderLayout(0, 6));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        labels.setFont(LABEL_FONT);
        add(timeline, BorderLayout.CENTER);
        add(labels, BorderLayout.SOUTH);
        showPlaceholder();
    }

    private void showPlaceholder() {
        timeline.set(null, null);
        labels.setText("일출 —   일몰 —   (지도를 클릭하세요)");
        labels.setForeground(Color.GRAY);
    }

    public void update(SunTimeInfo s) {
        if (s == null) {
            showError();
            return;
        }
        timeline.set(s.sunrise(), s.sunset());
        labels.setForeground(Color.DARK_GRAY);
        labels.setText("일출 " + s.sunrise() + "    일몰 " + s.sunset());
    }

    public void showError() {
        timeline.set(null, null);
        labels.setForeground(new Color(207, 34, 46));
        labels.setText("불러오기 실패");
    }

    static class DayTimeline extends JComponent {
        private LocalTime sunrise, sunset;
        DayTimeline() { setPreferredSize(new Dimension(260, 70)); }
        void set(LocalTime sr, LocalTime ss) { this.sunrise = sr; this.sunset = ss; repaint(); }

        private static double frac(LocalTime t) { return (t.toSecondOfDay()) / 86400.0; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int barY = 18, barH = 26, barX = 4, barW = w - 8;

            // 밤(회색) 배경
            g2.setColor(new Color(90, 100, 120));
            g2.fillRect(barX, barY, barW, barH);

            // 낮(노란 호)
            if (sunrise != null && sunset != null) {
                double f1 = frac(sunrise), f2 = frac(sunset);
                int x1 = barX + (int) (barW * f1);
                int x2 = barX + (int) (barW * f2);
                if (x2 > x1) {
                    g2.setColor(new Color(255, 214, 51));
                    g2.fillRect(x1, barY, x2 - x1, barH);
                    // 호(arc)로 해의 궤적 표현
                    g2.setColor(new Color(240, 160, 0));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawArc(x1, barY - 8, x2 - x1, barH + 16, 0, 180);
                }
            }

            // 시간 눈금 (0,6,12,18,24)
            g2.setColor(new Color(60, 60, 60));
            g2.setFont(new Font("Malgun Gothic", Font.PLAIN, 9));
            for (int hr = 0; hr <= 24; hr += 6) {
                int x = barX + (int) (barW * (hr / 24.0));
                if (hr == 24) x = barX + barW - 1;
                g2.drawLine(x, barY + barH, x, barY + barH + 3);
                g2.drawString(hr + "h", Math.min(x, barX + barW - 14), barY + barH + 14);
            }

            // 현재 시각 세로선
            double nowF = frac(LocalTime.now());
            int nx = barX + (int) (barW * nowF);
            g2.setColor(new Color(207, 34, 46));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(nx, barY - 6, nx, barY + barH + 4);
            g2.fillPolygon(new int[]{nx - 4, nx + 4, nx}, new int[]{barY - 10, barY - 10, barY - 4}, 3);
            g2.dispose();
        }
    }
}
