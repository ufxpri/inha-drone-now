package com.drone.view;

import com.drone.model.dto.WeatherInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 기상 정보 패널. 각 지표를 Java2D 커스텀 컴포넌트로 시각화한다.
 *   풍속 : 게이지 바 (0~15 m/s, 색상 단계)
 *   풍향 : 나침반 (바늘이 풍향을 가리킴)
 *   강수 : 빗방울 아이콘 + 파란 바 (0 이면 회색)
 *   기온 : 색상 숫자 (차가우면 파랑, 더우면 빨강)
 */
public class WeatherPanel extends JPanel {
    private static final Font LABEL_FONT = new Font("Malgun Gothic", Font.BOLD, 12);
    private static final Font VALUE_FONT = new Font("Malgun Gothic", Font.PLAIN, 12);

    private final WindGauge windGauge = new WindGauge();
    private final Compass compass = new Compass();
    private final RainBar rainBar = new RainBar();
    private final TempLabel tempLabel = new TempLabel();

    public WeatherPanel() {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        addRow(0, "풍속", windGauge);
        addRow(1, "풍향", compass);
        addRow(2, "강수", rainBar);
        addRow(3, "기온", tempLabel);

        showPlaceholder();
    }

    private void addRow(int row, String label, JComponent visual) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 0, 4, 10);
        JLabel l = new JLabel(label);
        l.setFont(LABEL_FONT);
        add(l, lc);

        GridBagConstraints vc = new GridBagConstraints();
        vc.gridx = 1; vc.gridy = row;
        vc.weightx = 1.0;
        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.insets = new Insets(4, 0, 4, 0);
        add(visual, vc);
    }

    private void showPlaceholder() {
        windGauge.setValue(Double.NaN);
        compass.setDegrees(Double.NaN);
        rainBar.setValue(Double.NaN);
        tempLabel.setValue(Double.NaN);
    }

    public void update(WeatherInfo w) {
        if (w == null) {
            showError();
            return;
        }
        windGauge.setValue(w.windSpeed());
        compass.setDegrees(w.windDir());
        rainBar.setValue(w.rainfall());
        tempLabel.setValue(w.tempC());
    }

    /** API 실패 시 빈 화면 대신 "불러오기 실패" 표시. */
    public void showError() {
        windGauge.setError();
        compass.setError();
        rainBar.setError();
        tempLabel.setError();
    }

    // ===== 풍속 게이지 바 =====
    static class WindGauge extends JComponent {
        private double value = Double.NaN;
        private boolean error = false;
        WindGauge() { setPreferredSize(new Dimension(160, 22)); }
        void setValue(double v) { this.value = v; this.error = false; repaint(); }
        void setError() { this.value = Double.NaN; this.error = true; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int barH = 14, barY = (h - barH) / 2, barW = w - 56;
            g2.setColor(new Color(225, 225, 225));
            g2.fill(new RoundRectangle2D.Float(0, barY, barW, barH, 8, 8));
            g2.setFont(VALUE_FONT);
            if (error) {
                g2.setColor(new Color(207, 34, 46));
                g2.drawString("실패", barW + 6, barY + barH - 2);
                g2.dispose(); return;
            }
            if (Double.isNaN(value)) {
                g2.setColor(Color.GRAY);
                g2.drawString("—", barW + 6, barY + barH - 2);
                g2.dispose(); return;
            }
            double frac = Math.max(0, Math.min(1, value / 15.0));
            Color c = value < 8 ? new Color(46, 160, 67)
                    : value <= 12 ? new Color(232, 142, 0)
                    : new Color(207, 34, 46);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(0, barY, (float) (barW * frac), barH, 8, 8));
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(String.format("%.1f m/s", value), barW + 6, barY + barH - 2);
            g2.dispose();
        }
    }

    // ===== 풍향 나침반 =====
    static class Compass extends JComponent {
        private double deg = Double.NaN;
        private boolean error = false;
        Compass() { setPreferredSize(new Dimension(160, 56)); }
        void setDegrees(double d) { this.deg = d; this.error = false; repaint(); }
        void setError() { this.deg = Double.NaN; this.error = true; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            int d = Math.min(h - 6, 48);
            int cx = d / 2 + 2, cy = h / 2;
            int r = d / 2;
            g2.setColor(new Color(245, 245, 245));
            g2.fill(new Ellipse2D.Float(cx - r, cy - r, d, d));
            g2.setColor(Color.GRAY);
            g2.draw(new Ellipse2D.Float(cx - r, cy - r, d, d));
            g2.setFont(new Font("Malgun Gothic", Font.PLAIN, 9));
            g2.drawString("N", cx - 3, cy - r + 9);

            g2.setFont(VALUE_FONT);
            if (error) {
                g2.setColor(new Color(207, 34, 46));
                g2.drawString("실패", cx + r + 8, cy + 4);
                g2.dispose(); return;
            }
            if (Double.isNaN(deg)) {
                g2.setColor(Color.GRAY);
                g2.drawString("—", cx + r + 8, cy + 4);
                g2.dispose(); return;
            }
            // 풍향(불어오는 방향). 화살표는 바람이 가리키는 쪽으로.
            double rad = Math.toRadians(deg);
            int ex = cx + (int) (Math.sin(rad) * (r - 5));
            int ey = cy - (int) (Math.cos(rad) * (r - 5));
            g2.setColor(new Color(207, 34, 46));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawLine(cx, cy, ex, ey);
            g2.fillOval(cx - 2, cy - 2, 4, 4);
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(String.format("%.0f°", deg), cx + r + 8, cy + 4);
            g2.dispose();
        }
    }

    // ===== 강수 바 + 빗방울 =====
    static class RainBar extends JComponent {
        private double value = Double.NaN;
        private boolean error = false;
        RainBar() { setPreferredSize(new Dimension(160, 22)); }
        void setValue(double v) { this.value = v; this.error = false; repaint(); }
        void setError() { this.value = Double.NaN; this.error = true; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            // 빗방울 아이콘
            int dropX = 8, dropY = h / 2;
            boolean dry = !error && !Double.isNaN(value) && value <= 0.0;
            Color dropColor = error ? new Color(207, 34, 46)
                    : (Double.isNaN(value) || dry) ? new Color(170, 170, 170)
                    : new Color(40, 120, 220);
            g2.setColor(dropColor);
            Polygon drop = new Polygon();
            drop.addPoint(dropX, dropY - 7);
            drop.addPoint(dropX - 5, dropY + 3);
            drop.addPoint(dropX + 5, dropY + 3);
            g2.fillPolygon(drop);
            g2.fillOval(dropX - 5, dropY - 1, 10, 9);

            int barX = 22, barH = 14, barY = (h - barH) / 2, barW = getWidth() - barX - 50;
            g2.setColor(new Color(225, 225, 225));
            g2.fill(new RoundRectangle2D.Float(barX, barY, barW, barH, 8, 8));
            g2.setFont(VALUE_FONT);
            if (error) {
                g2.setColor(new Color(207, 34, 46));
                g2.drawString("실패", barX + barW + 6, barY + barH - 2);
                g2.dispose(); return;
            }
            if (Double.isNaN(value)) {
                g2.setColor(Color.GRAY);
                g2.drawString("—", barX + barW + 6, barY + barH - 2);
                g2.dispose(); return;
            }
            double frac = Math.max(0, Math.min(1, value / 20.0));
            if (!dry) {
                g2.setColor(new Color(40, 120, 220));
                g2.fill(new RoundRectangle2D.Float(barX, barY, (float) (barW * frac), barH, 8, 8));
            }
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(String.format("%.1f mm", value), barX + barW + 6, barY + barH - 2);
            g2.dispose();
        }
    }

    // ===== 기온 (색상 숫자 + 온도계) =====
    static class TempLabel extends JComponent {
        private double value = Double.NaN;
        private boolean error = false;
        TempLabel() { setPreferredSize(new Dimension(160, 22)); }
        void setValue(double v) { this.value = v; this.error = false; repaint(); }
        void setError() { this.value = Double.NaN; this.error = true; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            // 온도계 모양
            int tx = 8, barH = 12, barY = (h - barH) / 2;
            g2.setColor(new Color(225, 225, 225));
            g2.fill(new RoundRectangle2D.Float(tx, barY, 80, barH, 6, 6));
            g2.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
            if (error) {
                g2.setColor(new Color(207, 34, 46));
                g2.drawString("불러오기 실패", tx + 92, barY + barH);
                g2.dispose(); return;
            }
            if (Double.isNaN(value)) {
                g2.setColor(Color.GRAY);
                g2.drawString("—", tx + 92, barY + barH);
                g2.dispose(); return;
            }
            // -10~40 범위 매핑
            double frac = Math.max(0, Math.min(1, (value + 10) / 50.0));
            Color c = value < 5 ? new Color(40, 120, 220)
                    : value < 28 ? new Color(46, 160, 67)
                    : new Color(207, 34, 46);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(tx, barY, (float) (80 * frac), barH, 6, 6));
            g2.drawString(String.format("%.1f °C", value), tx + 92, barY + barH);
            g2.dispose();
        }
    }
}
