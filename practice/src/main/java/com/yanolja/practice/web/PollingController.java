package com.yanolja.practice.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [실습 2] Polling (Short / Long)
 *
 * - Short Polling: 클라이언트가 주기적으로 /events 를 호출. 새 이벤트가 없으면 빈 배열.
 * - Long Polling : 클라이언트가 /long-poll 호출 → 서버는 새 이벤트가 생길 때까지 응답 보류.
 *   /publish 로 이벤트를 발생시키면 대기 중인 모든 요청에 즉시 응답한다.
 */
@RestController
@RequestMapping("/api/polling")
@CrossOrigin
public class PollingController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Short Polling용: 마지막으로 발생한 이벤트들 (간단히 메모리에 보관)
    private final Queue<Event> recent = new ConcurrentLinkedQueue<>();

    // Long Polling용: 응답을 보류 중인 요청들
    private final List<DeferredResult<List<Event>>> waiting = new CopyOnWriteArrayList<>();

    /** Short Polling: sinceId 이후에 발생한 이벤트만 반환 */
    @GetMapping("/events")
    public List<Event> shortPoll(@RequestParam(defaultValue = "0") long sinceId) {
        return recent.stream().filter(e -> e.id() > sinceId).toList();
    }

    /** Long Polling: 새 이벤트가 생길 때까지 최대 30초 보류, 없으면 빈 배열 */
    @GetMapping("/long-poll")
    public DeferredResult<List<Event>> longPoll() {
        DeferredResult<List<Event>> deferred = new DeferredResult<>(30_000L, List.of());
        waiting.add(deferred);
        // 완료/타임아웃 시 대기 목록에서 제거 (메모리 누수 방지)
        deferred.onCompletion(() -> waiting.remove(deferred));
        deferred.onTimeout(() -> waiting.remove(deferred));
        return deferred; // 톰캣 워커 스레드는 즉시 반납됨
    }

    /** 이벤트 발생: short polling 큐에 적재 + long polling 대기 요청에 즉시 응답 */
    @PostMapping("/publish")
    public Event publish(@RequestParam(defaultValue = "새 알림") String message) {
        Event event = new Event(System.currentTimeMillis(), message, LocalTime.now().format(TIME));

        recent.add(event);
        while (recent.size() > 20) {
            recent.poll(); // 최근 20개만 유지
        }

        // 보류 중이던 long polling 요청들에 즉시 응답
        for (DeferredResult<List<Event>> deferred : waiting) {
            deferred.setResult(List.of(event));
        }
        return event;
    }

    public record Event(long id, String message, String time) {}
}
