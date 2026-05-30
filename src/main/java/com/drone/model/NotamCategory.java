package com.drone.model;

import java.awt.Color;

/**
 * NOTAM을 드론 비행 관점의 경보 종류로 분류한다.
 * ICAO Q-code 형식은 {@code Q + 주제(2) + 상태(2)}이며, 주제 첫 글자(인덱스 1)로 대분류한다.
 *   R = 공역 제한/위험/금지, W = 항행 경고(훈련·사격·낙하 등),
 *   A·P = 공역 구성/관제 절차, 그 외 = 시설/기타.
 * 패널의 체크박스 필터와 지도 라벨/색에 공통으로 쓰인다.
 */
public enum NotamCategory {
    PROHIBITED("비행금지·제한", "금지", new Color(207, 34, 46)),
    WARNING("경고·활동", "경고", new Color(232, 142, 0)),
    AIRSPACE("공역·절차", "공역", new Color(40, 120, 200)),
    OTHER("시설·기타", "기타", new Color(120, 120, 120));

    /** 필터 체크박스/범례용 전체 이름. */
    public final String label;
    /** 지도 라벨용 짧은 이름. */
    public final String shortLabel;
    /** 지도 원·라벨 및 배지 색. */
    public final Color color;

    NotamCategory(String label, String shortLabel, Color color) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.color = color;
    }

    public static NotamCategory classify(String qcode, boolean prohibited) {
        if (prohibited) return PROHIBITED;
        String q = qcode == null ? "" : qcode.toUpperCase();
        char subject = q.length() >= 2 ? q.charAt(1) : ' ';
        return switch (subject) {
            case 'R' -> PROHIBITED;        // 제한·위험·금지구역
            case 'W' -> WARNING;           // 항행 경고(훈련/사격/낙하/드론 활동 등)
            case 'A', 'P' -> AIRSPACE;     // 공역 구성 / 관제 절차
            default -> OTHER;              // 시설·통신·등화·항행원조 등
        };
    }
}
