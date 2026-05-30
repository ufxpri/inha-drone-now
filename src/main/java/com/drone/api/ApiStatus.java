package com.drone.api;

import java.awt.Color;

/** 외부 데이터 소스(API/로컬 번들)의 마지막 조회 결과 상태. 풋터 상태바에 색점으로 표시된다. */
public enum ApiStatus {
    IDLE("대기", new Color(150, 150, 150)),
    LOADING("조회 중", new Color(232, 142, 0)),
    LIVE("실시간", new Color(46, 160, 67)),
    CACHED("캐시", new Color(70, 130, 220)),
    FAILED("실패", new Color(207, 34, 46));

    public final String label;
    public final Color color;

    ApiStatus(String label, Color color) {
        this.label = label;
        this.color = color;
    }
}
