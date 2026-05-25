package com.drone.view;

import com.drone.model.dto.NotamInfo;
import com.drone.model.dto.NotamInfo.NotamItem;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NotamPanel extends JPanel {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final DefaultListModel<NotamItem> model = new DefaultListModel<>();
    private final JList<NotamItem> list = new JList<>(model);
    private final JLabel header = new JLabel("NOTAM");

    public NotamPanel() {
        setLayout(new BorderLayout(6, 6));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        header.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        add(header, BorderLayout.NORTH);

        list.setBackground(Color.WHITE);
        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> renderItem(value, isSelected));
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        add(sp, BorderLayout.CENTER);

        showPlaceholder();
    }

    private JComponent renderItem(NotamItem value, boolean selected) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(selected ? new Color(225, 235, 255) : Color.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 235, 235)),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));

        JLabel badge = new JLabel("⚠");
        badge.setForeground(new Color(232, 142, 0));
        badge.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        row.add(badge, BorderLayout.WEST);

        String period = "";
        if (value.from() != null && value.to() != null) {
            period = value.from().format(FMT) + " ~ " + value.to().format(FMT);
        }
        JLabel lbl = new JLabel("<html><b>" + esc(value.area()) + "</b><br/>"
                + esc(value.content())
                + (period.isEmpty() ? "" : "<br/><span style='color:#888;font-size:9px'>" + period + "</span>")
                + "</html>");
        lbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        row.add(lbl, BorderLayout.CENTER);
        return row;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showPlaceholder() {
        model.clear();
        header.setText("NOTAM — 지도를 클릭하세요");
        header.setForeground(Color.GRAY);
    }

    public void update(NotamInfo n) {
        model.clear();
        if (n == null) {
            showError();
            return;
        }
        List<NotamItem> items = n.items();
        header.setForeground(Color.DARK_GRAY);
        header.setText("NOTAM — " + (items == null ? 0 : items.size()) + "건");
        if (items != null) items.forEach(model::addElement);
    }

    public void showError() {
        model.clear();
        header.setForeground(new Color(207, 34, 46));
        header.setText("NOTAM — 불러오기 실패");
    }
}
