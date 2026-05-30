package com.drone.view;

import com.drone.model.dto.RainForecastInfo;
import com.drone.model.dto.SunTimeInfo;
import com.drone.model.dto.WeatherInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 기상 + 일출/일몰 시각화 <b>홀더</b>.
 * 화면에 직접 붙는 컨테이너가 아니라, 하단 패널의 최상위 카드로 격상해 배치할 수 있도록
 * 4개 시각화(강수확률 · 풍속/풍향 · 일출/일몰 · 기온)를 패딩된 컴포넌트로 노출한다.
 * 컨트롤러는 {@link #update(WeatherInfo, RainForecastInfo, SunTimeInfo)} 한 번으로 전부 갱신한다.
 */
public class ConditionsPanel {
    private static final Font VALUE_FONT = new Font("Malgun Gothic", Font.PLAIN, 12);
    private static final Color CRITICAL = new Color(207, 34, 46);
    private static final Color MUTED = new Color(120, 120, 120);

    private final PopChart popChart = new PopChart();
    private final WindGauge windGauge = new WindGauge();
    private final Compass compass = new Compass();
    private final DayTimeline timeline = new DayTimeline();
    private final TempLabel tempLabel = new TempLabel();

    private final JComponent rainCard = pad(popChart);
    private final JComponent windCard = pad(buildWindBox());
    private final JComponent sunCard = pad(timeline);
    private final JComponent tempCard = pad(tempLabel);

    public ConditionsPanel() {
        showPlaceholder();
    }

    /** 하단 카드로 배치할 시각화(이미 흰 배경 + 패딩 적용). */
    public JComponent rainVisual() { return rainCard; }
    public JComponent windVisual() { return windCard; }
    public JComponent sunVisual() { return sunCard; }
    public JComponent tempVisual() { return tempCard; }

    private static JComponent pad(JComponent v) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    /** 풍속 게이지(위) + 풍향 나침반(아래)을 한 카드에 세로로 묶는다. */
    private JComponent buildWindBox() {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        windGauge.setPreferredSize(new Dimension(120, 34));
        box.add(windGauge, BorderLayout.NORTH);
        box.add(compass, BorderLayout.CENTER);
        return box;
    }

    private void showPlaceholder() {
        popChart.setData(null);
        windGauge.setValue(Double.NaN);
        compass.setDegrees(Double.NaN);
        timeline.set(null, null);
        tempLabel.setValue(Double.NaN);
    }

    /** 기상 실황(w)·강수예보(f)·일출일몰(s)을 한 번에 반영한다. 각 인자는 실패 시 null. */
    public void update(WeatherInfo w, RainForecastInfo f, SunTimeInfo s) {
        if (w == null) {
            windGauge.setError(); compass.setError(); tempLabel.setError();
        } else {
            windGauge.setValue(w.windSpeed());
            compass.setDegrees(w.windDir());
            tempLabel.setValue(w.tempC());
        }
        if (f == null) popChart.setError(); else popChart.setData(f.hours());
        if (s == null) timeline.setError(); else timeline.set(s.sunrise(), s.sunset());
    }

    /**
     * 5개 시각화의 공통 부모. 매번 반복되던 Graphics2D 준비/정리(안티에일리어싱·dispose)와
     * 에러 플래그 처리를 여기서 한 번만 맡는다. 자식은 {@link #paint2D}에서 그리기에만 집중한다.
     */
    abstract static class Chart extends JComponent {
        boolean error = false;
        Chart(int w, int h) { setPreferredSize(new Dimension(w, h)); }
        /** 에러 표시로 전환(값은 비운다). */
        void setError() { error = true; clearData(); repaint(); }
        /** 값을 비운다(에러/플레이스홀더 시). */
        abstract void clearData();
        /** 준비된 Graphics2D에 실제로 그린다(create/dispose는 부모가 처리). */
        abstract void paint2D(Graphics2D g2);

        @Override protected final void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paint2D(g2);
            g2.dispose();
        }
    }

    // ===== 강수확률 시계열 막대 =====
    static class PopChart extends Chart {
        private List<RainForecastInfo.Hourly> data;
        PopChart() { super(200, 120); }
        void setData(List<RainForecastInfo.Hourly> d) { this.data = d; this.error = false; repaint(); }
        @Override void clearData() { data = null; }

        @Override void paint2D(Graphics2D g2) {
            int w = getWidth(), h = getHeight();
            g2.setFont(VALUE_FONT);
            if (error) {
                g2.setColor(CRITICAL);
                g2.drawString("예보 불러오기 실패", 2, h / 2);
                return;
            }
            if (data == null || data.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("—  (지도를 클릭하세요)", 2, h / 2);
                return;
            }
            // 상단 요약: 최대 강수확률과 시각
            int maxPop = 0, maxHour = -1;
            for (RainForecastInfo.Hourly hh : data) {
                if (hh.pop() > maxPop) { maxPop = hh.pop(); maxHour = hh.time().getHour(); }
            }
            g2.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            g2.setColor(maxPop < 30 ? new Color(90, 150, 90) : maxPop < 60 ? new Color(200, 130, 40) : CRITICAL);
            g2.drawString(maxPop == 0 ? "강수확률 0% (안정)" : "최대 " + maxPop + "% (" + maxHour + "시)", 0, 13);

            int n = data.size();
            int axisY = h - 14;
            int top = 20;
            int chartH = Math.max(8, axisY - top);
            double bw = (double) w / n;

            for (int i = 0; i < n; i++) {
                RainForecastInfo.Hourly hh = data.get(i);
                double frac = Math.max(0, Math.min(1, hh.pop() / 100.0));
                int barH = (int) (chartH * frac);
                int bx = (int) (i * bw) + 2;
                int bwi = Math.max(4, (int) bw - 4);
                Color c = hh.pop() < 30 ? new Color(120, 190, 120)
                        : hh.pop() < 60 ? new Color(232, 160, 60)
                        : new Color(210, 70, 70);
                g2.setColor(new Color(238, 238, 238));
                g2.fill(new RoundRectangle2D.Float(bx, top, bwi, chartH, 4, 4));
                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Float(bx, axisY - barH, bwi, barH, 4, 4));
                // 확률 % (막대가 넓을 때만)
                g2.setFont(new Font("Malgun Gothic", Font.PLAIN, 9));
                String pct = hh.pop() + "%";
                int tw = g2.getFontMetrics().stringWidth(pct);
                if (bwi >= tw) {
                    g2.setColor(new Color(90, 90, 90));
                    g2.drawString(pct, bx + (bwi - tw) / 2, top - 2);
                }
                // 시각(시)
                String hr = String.format("%02d", hh.time().getHour());
                int hw = g2.getFontMetrics().stringWidth(hr);
                g2.setColor(MUTED);
                g2.drawString(hr, bx + (bwi - hw) / 2, h - 2);
            }
            g2.setColor(new Color(215, 215, 215));
            g2.drawLine(0, axisY, w, axisY);
        }
    }

    // ===== 풍속 게이지 (막대 위 + 값 아래, 좁은 카드용) =====
    static class WindGauge extends Chart {
        private double value = Double.NaN;
        WindGauge() { super(120, 34); }
        void setValue(double v) { this.value = v; this.error = false; repaint(); }
        @Override void clearData() { value = Double.NaN; }

        @Override void paint2D(Graphics2D g2) {
            int w = getWidth();
            int barH = 12, barY = 2;
            g2.setColor(new Color(225, 225, 225));
            g2.fill(new RoundRectangle2D.Float(0, barY, w, barH, 8, 8));
            g2.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            int textY = barY + barH + 14;
            if (error) {
                g2.setColor(CRITICAL);
                String t = "실패";
                g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, textY);
                return;
            }
            if (Double.isNaN(value)) {
                g2.setColor(Color.GRAY);
                g2.drawString("—", (w - g2.getFontMetrics().stringWidth("—")) / 2, textY);
                return;
            }
            double frac = Math.max(0, Math.min(1, value / 15.0));
            Color c = value < 8 ? new Color(46, 160, 67)
                    : value <= 12 ? new Color(232, 142, 0)
                    : new Color(207, 34, 46);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(0, barY, (float) (w * frac), barH, 8, 8));
            g2.setColor(c);
            String t = String.format("%.1f m/s", value);
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, textY);
        }
    }

    // ===== 풍향 나침반 =====
    static class Compass extends Chart {
        private double deg = Double.NaN;
        Compass() { super(120, 80); }
        void setDegrees(double d) { this.deg = d; this.error = false; repaint(); }
        @Override void clearData() { deg = Double.NaN; }

        @Override void paint2D(Graphics2D g2) {
            int w = getWidth(), h = getHeight();
            int d = Math.max(28, Math.min(Math.min(w - 6, h - 22), 72));
            int cx = w / 2, cy = h / 2 - 2;
            int r = d / 2;
            g2.setColor(new Color(245, 245, 245));
            g2.fill(new Ellipse2D.Float(cx - r, cy - r, d, d));
            g2.setColor(Color.GRAY);
            g2.draw(new Ellipse2D.Float(cx - r, cy - r, d, d));
            g2.setFont(new Font("Malgun Gothic", Font.PLAIN, 9));
            g2.drawString("N", cx - 3, cy - r + 9);

            g2.setFont(VALUE_FONT);
            if (error) {
                g2.setColor(CRITICAL);
                String t = "실패";
                g2.drawString(t, cx - g2.getFontMetrics().stringWidth(t) / 2, cy + r + 15);
                return;
            }
            if (Double.isNaN(deg)) {
                g2.setColor(Color.GRAY);
                g2.drawString("—", cx - 3, cy + r + 15);
                return;
            }
            double rad = Math.toRadians(deg);
            int ex = cx + (int) (Math.sin(rad) * (r - 5));
            int ey = cy - (int) (Math.cos(rad) * (r - 5));
            g2.setColor(new Color(207, 34, 46));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawLine(cx, cy, ex, ey);
            g2.fillOval(cx - 2, cy - 2, 4, 4);
            g2.setColor(Color.DARK_GRAY);
            String txt = String.format("%.0f°", deg);
            g2.drawString(txt, cx - g2.getFontMetrics().stringWidth(txt) / 2, cy + r + 15);
        }
    }

    // ===== 기온 (세로 온도계 바, 좁은 카드용) =====
    static class TempLabel extends Chart {
        // 매핑 범위 -10 ~ 40 ℃
        private static final double MIN = -10, MAX = 40;
        private double value = Double.NaN;
        TempLabel() { super(70, 120); }
        void setValue(double v) { this.value = v; this.error = false; repaint(); }
        @Override void clearData() { value = Double.NaN; }

        @Override void paint2D(Graphics2D g2) {
            int w = getWidth(), h = getHeight();
            g2.setFont(new Font("Malgun Gothic", Font.BOLD, 12));

            if (error || Double.isNaN(value)) {
                g2.setColor(error ? CRITICAL : Color.GRAY);
                String t = error ? "실패" : "—";
                g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, h / 2);
                return;
            }

            int numH = 18;                  // 하단 값 영역
            int top = 6, trackBottom = h - numH;
            int trackH = Math.max(10, trackBottom - top);
            int trackW = 14, trackX = w / 2 - trackW / 2;
            int bulbR = 9;

            Color c = value < 5 ? new Color(40, 120, 220)
                    : value < 28 ? new Color(46, 160, 67)
                    : new Color(207, 34, 46);

            // 트랙 배경 + 전구
            g2.setColor(new Color(228, 228, 228));
            g2.fill(new RoundRectangle2D.Float(trackX, top, trackW, trackH, trackW, trackW));
            g2.fillOval(w / 2 - bulbR, trackBottom - bulbR, bulbR * 2, bulbR * 2);

            // 채움(아래에서 위로)
            double frac = Math.max(0, Math.min(1, (value - MIN) / (MAX - MIN)));
            int fillH = (int) (trackH * frac);
            g2.setColor(c);
            g2.fill(new RoundRectangle2D.Float(trackX, top + (trackH - fillH), trackW, fillH, trackW, trackW));
            g2.fillOval(w / 2 - bulbR, trackBottom - bulbR, bulbR * 2, bulbR * 2);

            // 값 텍스트
            String t = String.format("%.1f°C", value);
            g2.setColor(c);
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, h - 4);
        }
    }

    // ===== 일출/일몰 타임라인 =====
    static class DayTimeline extends Chart {
        private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
        private LocalTime sunrise, sunset;
        DayTimeline() { super(200, 80); }
        void set(LocalTime sr, LocalTime ss) { this.sunrise = sr; this.sunset = ss; this.error = false; repaint(); }
        @Override void clearData() { sunrise = null; sunset = null; }

        private static double frac(LocalTime t) { return t.toSecondOfDay() / 86400.0; }

        @Override void paint2D(Graphics2D g2) {
            int w = getWidth(), h = getHeight();
            g2.setFont(VALUE_FONT);
            if (error) {
                g2.setColor(CRITICAL);
                g2.drawString("불러오기 실패", 2, h / 2);
                return;
            }
            int barH = 18;
            int barY = Math.max(22, h / 2 - barH / 2);
            int barX = 2, barW = w - 4;

            // 상단 텍스트(일출/일몰 시각)
            g2.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            int textY = barY - 8;
            if (sunrise != null && sunset != null) {
                g2.setColor(new Color(232, 142, 0));
                g2.drawString("☀ " + sunrise.format(HM), 0, textY);
                g2.setColor(new Color(90, 100, 140));
                String ss = "☾ " + sunset.format(HM);
                g2.drawString(ss, w - g2.getFontMetrics().stringWidth(ss), textY);
            } else {
                g2.setColor(Color.GRAY);
                g2.drawString("일출 —  일몰 —", 0, textY);
            }

            // 밤(회색) 배경
            g2.setColor(new Color(90, 100, 120));
            g2.fillRect(barX, barY, barW, barH);
            // 낮(노란 구간 + 해 궤적 호)
            if (sunrise != null && sunset != null) {
                int x1 = barX + (int) (barW * frac(sunrise));
                int x2 = barX + (int) (barW * frac(sunset));
                if (x2 > x1) {
                    g2.setColor(new Color(255, 214, 51));
                    g2.fillRect(x1, barY, x2 - x1, barH);
                    g2.setColor(new Color(240, 160, 0));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawArc(x1, barY - 6, x2 - x1, barH + 12, 0, 180);
                }
            }
            // 시간 눈금
            g2.setColor(new Color(70, 70, 70));
            g2.setFont(new Font("Malgun Gothic", Font.PLAIN, 9));
            for (int hr = 0; hr <= 24; hr += 6) {
                int x = barX + (int) (barW * (hr / 24.0));
                if (hr == 24) x = barX + barW - 1;
                g2.drawLine(x, barY + barH, x, barY + barH + 3);
                g2.drawString(hr + "h", Math.min(x, barX + barW - 14), barY + barH + 14);
            }
            // 현재 시각 표시선
            int nx = barX + (int) (barW * frac(LocalTime.now()));
            g2.setColor(new Color(207, 34, 46));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(nx, barY - 4, nx, barY + barH + 3);
            g2.fillPolygon(new int[]{nx - 4, nx + 4, nx}, new int[]{barY - 8, barY - 8, barY - 2}, 3);
        }
    }
}
