package com.drone.view;

import com.drone.controller.DashboardController;
import com.drone.io.NoFlyZoneStore;
import com.drone.model.Location;
import com.drone.model.dto.NotamInfo;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCursor;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapPanel extends JPanel {
    private final JXMapViewer viewer = new JXMapViewer();
    private final DashboardController controller;
    private final WaypointPainter<DefaultWaypoint> waypointPainter = new WaypointPainter<>();
    private final Set<DefaultWaypoint> waypoints = new HashSet<>();
    private final NotamCirclePainter notamPainter = new NotamCirclePainter();
    private final NoFlyPolygonPainter polygonPainter = new NoFlyPolygonPainter();
    private final JLabel statusLabel = new JLabel(" 선택된 위치: (지도를 클릭하세요)");

    public MapPanel(DashboardController controller) {
        this.controller = controller;

        // OSM 정책상 기본/없음 User-Agent는 거부됨. 전역 http.agent 설정.
        System.setProperty("http.agent", "DroneNow/1.0 (cfi2000@ogqcorp.com)");

        setLayout(new BorderLayout());

        // Carto Light basemap: UA 정책이 느슨하고 깔끔한 라이트 톤이라 VS Code 풍 UI에 맞음
        TileFactoryInfo info = new TileFactoryInfo(
                1, 17, 17,
                256, true, true,
                "https://a.basemaps.cartocdn.com/light_all",
                "x", "y", "z") {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                int invZoom = getTotalMapZoom() - zoom;
                return this.baseURL + "/" + invZoom + "/" + x + "/" + y + ".png";
            }
        };
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(4);
        // JxMapViewer는 내부 HttpURLConnection을 쓰며, 위 http.agent 시스템 프로퍼티가 적용됨.

        viewer.setTileFactory(tileFactory);
        // Carto는 zoom inversion(17 - z) 이라 표시 zoom은 0..16 범위를 양수로 줘야 함
        viewer.setZoom(10);
        viewer.setAddressLocation(new GeoPosition(37.5665, 126.9780));

        // 오버레이: NOTAM 원(아래) + 클릭 마커(위)를 함께 그린다.
        waypointPainter.setWaypoints(waypoints);
        // 그리기 순서: 공역 폴리곤(아래) → NOTAM 원 → 클릭 마커(위)
        CompoundPainter<JXMapViewer> overlay = new CompoundPainter<>(polygonPainter, notamPainter, waypointPainter);
        overlay.setCacheable(false);
        viewer.setOverlayPainter(overlay);

        PanMouseInputListener pan = new PanMouseInputListener(viewer);
        viewer.addMouseListener(pan);
        viewer.addMouseMotionListener(pan);
        viewer.addMouseWheelListener(new ZoomMouseWheelListenerCursor(viewer));
        viewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                    Rectangle bounds = viewer.getViewportBounds();
                    int x = bounds.x + e.getX();
                    int y = bounds.y + e.getY();
                    GeoPosition pos = viewer.getTileFactory().pixelToGeo(new Point(x, y), viewer.getZoom());
                    setMarker(pos);
                    statusLabel.setText(String.format(" 선택된 위치: %.5f, %.5f", pos.getLatitude(), pos.getLongitude()));
                    controller.onMapClicked(new Location(pos.getLatitude(), pos.getLongitude()));
                }
            }
        });

        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(230, 230, 230));
        statusLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));
        statusLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        add(statusLabel, BorderLayout.NORTH);
        add(viewer, BorderLayout.CENTER);
    }

    private void setMarker(GeoPosition pos) {
        waypoints.clear();
        waypoints.add(new DefaultWaypoint(pos));
        waypointPainter.setWaypoints(waypoints);
        viewer.repaint();
    }

    /** NOTAM 목록을 받아 지도에 원으로 표시한다. (EDT에서 호출) */
    public void setNotams(List<NotamInfo.NotamItem> items) {
        notamPainter.setItems(items == null ? List.of() : new ArrayList<>(items));
        viewer.repaint();
    }

    /** 번들 공역 폴리곤을 받아 지도에 표시한다. (EDT에서 호출) */
    public void setNoFlyPolygons(List<NoFlyZoneStore.ZonePolygon> polys) {
        polygonPainter.setPolygons(polys == null ? List.of() : new ArrayList<>(polys));
        viewer.repaint();
    }

    /** 공역 폴리곤을 분류별 색으로 반투명 채움 + 외곽선으로 그리는 오버레이 페인터. */
    private static final class NoFlyPolygonPainter implements Painter<JXMapViewer> {
        private List<NoFlyZoneStore.ZonePolygon> polys = List.of();

        void setPolygons(List<NoFlyZoneStore.ZonePolygon> polys) { this.polys = polys; }

        @Override
        public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
            if (polys.isEmpty()) return;
            g = (Graphics2D) g.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle vp = map.getViewportBounds();
            int zoom = map.getZoom();
            g.setStroke(new BasicStroke(1f));

            for (NoFlyZoneStore.ZonePolygon poly : polys) {
                List<double[]> ring = poly.ring();
                if (ring.size() < 3) continue;
                Path2D.Double path = new Path2D.Double();
                boolean first = true;
                for (double[] pt : ring) {
                    Point2D px = map.getTileFactory().geoToPixel(
                            new GeoPosition(pt[0], pt[1]), zoom);
                    double sx = px.getX() - vp.getX();
                    double sy = px.getY() - vp.getY();
                    if (first) {
                        path.moveTo(sx, sy);
                        first = false;
                    } else {
                        path.lineTo(sx, sy);
                    }
                }
                path.closePath();

                Color base = poly.category().color;
                g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 50));
                g.fill(path);
                g.setColor(base);
                g.draw(path);
            }
            g.dispose();
        }
    }

    /** hasGeometry()인 NOTAM을 반투명 원으로 그리는 오버레이 페인터. */
    private static final class NotamCirclePainter implements Painter<JXMapViewer> {
        private List<NotamInfo.NotamItem> items = List.of();

        void setItems(List<NotamInfo.NotamItem> items) { this.items = items; }

        @Override
        public void paint(Graphics2D g, JXMapViewer map, int w, int h) {
            if (items.isEmpty()) return;
            g = (Graphics2D) g.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle vp = map.getViewportBounds();
            int zoom = map.getZoom();

            for (NotamInfo.NotamItem it : items) {
                if (!it.hasGeometry()) continue;
                double cLat = it.centerLat(), cLon = it.centerLon();
                double radiusKm = it.radiusNm() * 1.852;

                // 중심을 화면 픽셀로 변환 (geoToPixel은 절대 월드 좌표 → 뷰포트 오프셋 차감)
                Point2D cPx = map.getTileFactory().geoToPixel(new GeoPosition(cLat, cLon), zoom);
                int cx = (int) (cPx.getX() - vp.getX());
                int cy = (int) (cPx.getY() - vp.getY());

                // 반경: 중심에서 북쪽으로 radiusKm 떨어진 점의 픽셀 y차이 (위도 1도 ≈ 111km)
                Point2D nPx = map.getTileFactory().geoToPixel(
                        new GeoPosition(cLat + radiusKm / 111.0, cLon), zoom);
                int pr = (int) Math.abs(nPx.getY() - cPx.getY());
                if (pr < 2) pr = 2;

                Color fill = it.prohibited() ? new Color(255, 0, 0, 60) : new Color(255, 150, 0, 40);
                Color line = it.prohibited() ? new Color(200, 0, 0) : new Color(220, 120, 0);
                g.setColor(fill);
                g.fillOval(cx - pr, cy - pr, pr * 2, pr * 2);
                g.setColor(line);
                g.setStroke(new BasicStroke(it.prohibited() ? 2f : 1f));
                g.drawOval(cx - pr, cy - pr, pr * 2, pr * 2);
            }
            g.dispose();
        }
    }
}
