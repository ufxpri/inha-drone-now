package com.drone.api;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 각 데이터 소스의 최신 조회 상태를 모아두고 변경 시 구독자(풋터 상태바)에게 알린다.
 *
 * <p>{@link #report}는 백그라운드(SwingWorker) 스레드에서 호출되고, 구독자(뷰)는 EDT에서
 * 갱신해야 하므로 EDT 전환 책임은 구독자에게 둔다(여기서는 스레드 안전한 저장 + 통지만 담당).
 */
public final class ApiStatusBus {

    /** 한 소스의 상태 스냅샷: 상태 + 상세 메시지(툴팁) + 갱신 시각. */
    public record Entry(ApiStatus status, String detail, LocalTime at) {}

    private final Map<String, Entry> states = new ConcurrentHashMap<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    /** 소스 키(예: "기상청")의 상태를 갱신하고 구독자에게 키를 통지한다. */
    public void report(String key, ApiStatus status, String detail) {
        states.put(key, new Entry(status, detail, LocalTime.now().withNano(0)));
        for (Consumer<String> l : listeners) l.accept(key);
    }

    /** 현재 상태(없으면 IDLE). */
    public Entry get(String key) {
        return states.getOrDefault(key, new Entry(ApiStatus.IDLE, "아직 조회하지 않음", null));
    }

    /** 상태 변경 구독(인자는 변경된 키). 구독자는 EDT 전환을 직접 처리해야 한다. */
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }
}
