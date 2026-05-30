package com.drone.model.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * 단기예보 기반 시간대별 강수확률(POP) 시계열.
 *   hours : 현재 시각 이후 가까운 순서로 정렬된 시간별 항목.
 */
public record RainForecastInfo(List<Hourly> hours) {
    /**
     * @param time 예보 시각
     * @param pop  강수확률(%) 0~100
     * @param pty  강수형태 0:없음 1:비 2:비/눈 3:눈 4:소나기
     */
    public record Hourly(LocalTime time, int pop, int pty) {
        public boolean isWet() { return pty > 0; }
    }
}
