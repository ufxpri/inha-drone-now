package com.drone.view;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * VS Code의 사이드바 섹션처럼 헤더를 클릭하면 본문이 접히는 패널.
 */
public class CollapsibleSection extends JPanel {
    private static final Color HEADER_BG = new Color(230, 230, 230);
    private static final Color HEADER_BG_HOVER = new Color(215, 220, 230);

    private final JLabel headerLabel;
    private final JComponent body;
    private boolean expanded = true;

    public CollapsibleSection(String title, JComponent body) {
        this.body = body;
        setLayout(new BorderLayout());
        setOpaque(false);

        headerLabel = new JLabel(formatTitle(title));
        headerLabel.setOpaque(true);
        headerLabel.setBackground(HEADER_BG);
        headerLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        headerLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        headerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { headerLabel.setBackground(HEADER_BG_HOVER); }
            @Override public void mouseExited(MouseEvent e) { headerLabel.setBackground(HEADER_BG); }
            @Override public void mouseClicked(MouseEvent e) { toggle(); }
        });

        add(headerLabel, BorderLayout.NORTH);
        Border bodyBorder = BorderFactory.createMatteBorder(0, 1, 1, 1, new Color(200, 200, 200));
        body.setBorder(BorderFactory.createCompoundBorder(bodyBorder, body.getBorder() == null
                ? BorderFactory.createEmptyBorder() : body.getBorder()));
        add(body, BorderLayout.CENTER);
    }

    private void toggle() {
        expanded = !expanded;
        body.setVisible(expanded);
        headerLabel.setText(formatTitle(rawTitle()));
        revalidate();
        repaint();
    }

    private String rawTitle() {
        String t = headerLabel.getText();
        if (t.startsWith("▾ ") || t.startsWith("▸ ")) return t.substring(2);
        return t;
    }

    private String formatTitle(String title) {
        return (expanded ? "▾ " : "▸ ") + title;
    }
}
