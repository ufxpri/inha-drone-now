package com.drone.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record NotamInfo(List<NotamItem> items) {
    /**
     * NOTAM 한 건. 기하정보(중심/반경)는 파싱 불가하거나 반경이 999(FIR 전역)면 null.
     */
    public record NotamItem(
            String area, String content,
            LocalDateTime from, LocalDateTime to,
            Double centerLat, Double centerLon, Double radiusNm,
            String qcode,
            boolean prohibited) {

        /** 좌표 없이 생성하던 기존 호출부 호환용 4-인자 생성자. */
        public NotamItem(String area, String content, LocalDateTime from, LocalDateTime to) {
            this(area, content, from, to, null, null, null, null, false);
        }

        /** 지도에 원으로 그릴 수 있는 기하정보가 모두 존재하는지. */
        public boolean hasGeometry() {
            return centerLat != null && centerLon != null && radiusNm != null;
        }
    }
}
