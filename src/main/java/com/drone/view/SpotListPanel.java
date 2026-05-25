package com.drone.view;

import com.drone.controller.SpotController;
import com.drone.model.Location;
import com.drone.model.Spot;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SpotListPanel extends JPanel {
    private final SpotController controller;
    private final DefaultListModel<Spot> model = new DefaultListModel<>();
    private final JList<Spot> list = new JList<>(model);

    /** 현재 지도에서 클릭된 위치 공급자(없으면 null 반환). */
    private final Supplier<Location> currentLocation;
    /** 저장된 지점을 클릭하면 해당 위치로 대시보드를 재실행하는 콜백(선택). */
    private final Consumer<Location> onSpotSelected;

    public SpotListPanel(SpotController controller) {
        this(controller, () -> null, null);
    }

    public SpotListPanel(SpotController controller,
                         Supplier<Location> currentLocation,
                         Consumer<Location> onSpotSelected) {
        this.controller = controller;
        this.currentLocation = currentLocation;
        this.onSpotSelected = onSpotSelected;

        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        list.setCellRenderer((l, value, index, isSelected, cellHasFocus) -> {
            String txt = String.format("%s  (%.4f, %.4f)",
                    value.name(), value.location().lat(), value.location().lon());
            JLabel lbl = new JLabel(txt);
            lbl.setOpaque(true);
            lbl.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            if (isSelected) lbl.setBackground(new Color(200, 220, 255));
            return lbl;
        });
        // 저장된 지점을 더블클릭하면 대시보드 재실행
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && onSpotSelected != null) {
                    Spot sel = list.getSelectedValue();
                    if (sel != null) onSpotSelected.accept(sel.location());
                }
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton add = new JButton("추가");
        JButton remove = new JButton("제거");
        buttons.add(add);
        buttons.add(remove);
        add(buttons, BorderLayout.SOUTH);

        add.addActionListener(e -> onAdd());
        remove.addActionListener(e -> {
            Spot sel = list.getSelectedValue();
            if (sel != null) {
                controller.removeSpot(sel);
                refresh();
            }
        });

        refresh();
    }

    private void onAdd() {
        Location loc = currentLocation == null ? null : currentLocation.get();
        if (loc == null) {
            JOptionPane.showMessageDialog(this, "지도에서 위치를 먼저 클릭하세요.",
                    "관심지점 추가", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JTextField nameField = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel(String.format("위도: %.5f    경도: %.5f", loc.lat(), loc.lon())));
        form.add(new JLabel("지점 이름:"));
        form.add(nameField);

        int res = JOptionPane.showConfirmDialog(this, form, "관심지점 추가",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String name = nameField.getText();
        if (name == null || name.isBlank()) {
            JOptionPane.showMessageDialog(this, "이름을 입력하세요.");
            return;
        }
        controller.addSpot(new Spot(name.trim(), loc, LocalDateTime.now()));
        refresh();
    }

    public void refresh() {
        model.clear();
        controller.listSpots().forEach(model::addElement);
    }
}
