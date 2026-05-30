package com.drone.view;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Swing(현재 Look&Feel)의 내장 리소스 아이콘을 가져와 원하는 크기로 축소해 제공한다.
 *
 * <p>이모지 글리프와 달리 폰트에 의존하지 않는 실제 이미지라 항상 렌더링되며,
 * 경고/오류/정보처럼 의미가 분명한 상태 표시에 사용한다.
 * (예: {@code UIManager} 키 "OptionPane.warningIcon" / "errorIcon" / "informationIcon")
 */
final class UiIcons {
    private UiIcons() {}

    /** UIManager 아이콘을 size×size 픽셀로 부드럽게 축소(없으면 null). */
    static Icon scaled(String uiManagerKey, int size) {
        Icon src = UIManager.getIcon(uiManagerKey);
        if (src == null) return null;
        int w = Math.max(1, src.getIconWidth());
        int h = Math.max(1, src.getIconHeight());
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buf.createGraphics();
        src.paintIcon(null, g, 0, 0);
        g.dispose();
        Image scaled = buf.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }
}
