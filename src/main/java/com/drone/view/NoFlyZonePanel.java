package com.drone.view;

import com.drone.model.dto.NoFlyZoneInfo;
import com.drone.model.dto.NoFlyZoneInfo.Zone;

import javax.swing.*;
import java.awt.*;

/**
 * 비행금지구역 패널. 상단에 큰 방패 상태 표시(가능/금지), 하단에 구역 목록.
 */
public class NoFlyZonePanel extends JPanel {
    private final ShieldStatus shield = new ShieldStatus();
    private final DefaultListModel<Zone> model = new DefaultListModel<>();
    private final JList<Zone> list = new JList<>(model);

    public NoFlyZonePanel() {
        setLayout(new BorderLayout(6, 6));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        add(shield, BorderLayout.NORTH);

        list.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = new JLabel("• " + value.name() + " [" + value.type() + "]");
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            if (isSelected) lbl.setBackground(new Color(255, 220, 200));
            return lbl;
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        add(sp, BorderLayout.CENTER);

        showPlaceholder();
    }

    private void showPlaceholder() {
        shield.set(ShieldStatus.State.UNKNOWN, "지도를 클릭하세요");
        model.clear();
    }

    public void update(NoFlyZoneInfo z) {
        model.clear();
        if (z == null) {
            showError();
            return;
        }
        if (z.intersects()) {
            // 방패 아이콘이 ✕ 마크를 직접 그리므로 텍스트에는 기호를 넣지 않는다.
            shield.set(ShieldStatus.State.BLOCKED, "비행금지구역 교차 (" + z.zones().size() + "건)");
        } else {
            shield.set(ShieldStatus.State.OK, "비행 가능 구역");
        }
        if (z.zones() != null) z.zones().forEach(model::addElement);
    }

    public void showError() {
        shield.set(ShieldStatus.State.UNKNOWN, "불러오기 실패");
        model.clear();
    }

    static class ShieldStatus extends JComponent {
        enum State { UNKNOWN, OK, BLOCKED }
        private State state = State.UNKNOWN;
        private String text = "";
        ShieldStatus() { setPreferredSize(new Dimension(240, 52)); }
        void set(State s, String t) { this.state = s; this.text = t; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            Color c = switch (state) {
                case OK -> new Color(46, 160, 67);
                case BLOCKED -> new Color(207, 34, 46);
                case UNKNOWN -> new Color(150, 150, 150);
            };
            // 방패 모양
            int sx = 6, sy = 6, sw = 32, sh = h - 12;
            Polygon shield = new Polygon();
            shield.addPoint(sx + sw / 2, sy);
            shield.addPoint(sx + sw, sy + 6);
            shield.addPoint(sx + sw, sy + sh - 12);
            shield.addPoint(sx + sw / 2, sy + sh);
            shield.addPoint(sx, sy + sh - 12);
            shield.addPoint(sx, sy + 6);
            g2.setColor(c);
            g2.fillPolygon(shield);
            // 체크/엑스 마크
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3f));
            int mcx = sx + sw / 2, mcy = sy + sh / 2 - 2;
            if (state == State.OK) {
                g2.drawLine(mcx - 7, mcy, mcx - 2, mcy + 6);
                g2.drawLine(mcx - 2, mcy + 6, mcx + 8, mcy - 6);
            } else if (state == State.BLOCKED) {
                g2.drawLine(mcx - 6, mcy - 6, mcx + 6, mcy + 6);
                g2.drawLine(mcx + 6, mcy - 6, mcx - 6, mcy + 6);
            } else {
                g2.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
                g2.drawString("?", mcx - 4, mcy + 6);
            }
            g2.setColor(c.darker());
            g2.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
            g2.drawString(text, sx + sw + 12, h / 2 + 5);
            g2.dispose();
        }
    }
}
