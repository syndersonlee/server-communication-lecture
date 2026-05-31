package com.yanolja.practice.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * [실습 3] SSE (Server-Sent Events)
 *
 * - /subscribe : 클라이언트가 연결을 열고 유지 (text/event-stream). 서버가 단방향으로 push.
 * - /broadcast : 구독 중인 모든 클라이언트에게 이벤트 전송.
 * - /prices    : 1초마다 자동으로 가격을 흘려보내는 스트림 (서버 푸시 자동 데모).
 */
@RestController
@RequestMapping("/api/sse")
@CrossOrigin
public class SseController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong eventId = new AtomicLong(0);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** 구독: 1시간짜리 SSE 연결을 연다 */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            // 연결 직후 인사 이벤트 (프록시가 빈 응답을 끊지 않도록 하는 효과도 있음)
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE 연결 성공! 현재 구독자 수: " + emitters.size()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** 모든 구독자에게 단방향 push */
    @PostMapping("/broadcast")
    public String broadcast(@RequestParam(defaultValue = "안녕하세요!") String message) {
        long id = eventId.incrementAndGet();
        String payload = "[" + LocalTime.now().format(TIME) + "] " + message;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(id))
                        .name("notification")
                        .data(payload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
        return "전송 완료 (구독자 " + emitters.size() + "명)";
    }

    /** 1초마다 가격 변동을 자동으로 흘려보내는 독립 스트림 (10회 후 종료) */
    @GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prices() {
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicLong tick = new AtomicLong(0);
        // 스케줄 작업을 종료 시 취소하기 위해 참조를 보관
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Runnable task = () -> {
            long n = tick.incrementAndGet();
            try {
                int price = 100_000 + (int) (Math.random() * 20_000) - 10_000;
                emitter.send(SseEmitter.event()
                        .name("price")
                        .data("{\"tick\":" + n + ",\"price\":" + price + "}"));
                if (n >= 10) {
                    emitter.complete();
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);
        futureRef.set(future);
        // 스트림이 끝나면(완료/타임아웃/에러) 스케줄도 반드시 취소
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(e -> future.cancel(true));
        return emitter;
    }
}
