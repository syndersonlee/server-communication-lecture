# 05. 실시간 통신 ② SSE (Server-Sent Events)

> "서버가 클라이언트한테 일방적으로 계속 보내기만 하면 되는" 경우가 의외로 많습니다.
> 알림, 실시간 피드, 진행률, 주식 시세, LLM 토큰 스트리밍... 이럴 때 가장 깔끔한 답이 **SSE**.

---

## 5.1 SSE란

**SSE(Server-Sent Events)**: 하나의 HTTP 연결을 열어두고, **서버가 클라이언트로 단방향**으로
이벤트를 계속 흘려보내는(streaming) 표준 기술. (`text/event-stream`)

```
Client ───────(GET, 연결 유지)───────▶ Server
       ◀── event: notify ────────────  (서버가 계속 push)
       ◀── event: notify ────────────
       ◀── event: notify ────────────
       (한 번 열린 연결로 서버가 계속 보냄, 클라는 받기만)
```

핵심 특징:
- **단방향**: 서버 → 클라이언트만. (클라가 서버로 보내려면 별도 HTTP 요청)
- **HTTP 기반**: 별도 프로토콜 업그레이드 불필요. 그냥 응답을 안 끝내고 계속 보냄.
- **자동 재연결**: 연결이 끊기면 브라우저(EventSource)가 알아서 재접속. `Last-Event-ID`로 이어받기 지원.
- **텍스트 기반**: UTF-8 텍스트. 바이너리는 부적합.

---

## 5.2 SSE 메시지 포맷

서버는 `text/event-stream` Content-Type으로 아래 형식의 텍스트를 흘려보냅니다.

```
event: notification
id: 1001
data: {"message": "예약이 확정되었습니다", "reservationId": 9001}

event: notification
id: 1002
data: {"message": "체크인 24시간 전입니다"}

: 이건 주석(콜론으로 시작), heartbeat 용도로도 사용
```

- 필드: `event`(이벤트 타입), `data`(본문), `id`(재연결 시 이어받기용), `retry`(재연결 간격 ms).
- 각 이벤트는 **빈 줄**로 구분.

---

## 5.3 클라이언트 — EventSource (브라우저 내장)

```javascript
const es = new EventSource('/api/notifications/subscribe?userId=9001');

// 기본 메시지
es.onmessage = (e) => console.log('data:', e.data);

// 커스텀 이벤트 타입(event: notification)
es.addEventListener('notification', (e) => {
  const data = JSON.parse(e.data);
  showToast(data.message);
});

es.onerror = (e) => {
  // 끊겨도 브라우저가 자동 재연결 시도 (retry 간격대로)
  console.warn('SSE error', e);
};
```

> 💡 `EventSource`는 끊기면 **자동 재연결**해주고, 마지막 받은 `id`를 `Last-Event-ID` 헤더로
> 다시 보내줍니다. 서버는 이 값으로 "어디부터 다시 보낼지" 결정할 수 있어요.

---

## 5.4 Spring에서 SSE 구현 — SseEmitter

### 서버 코드

```java
@RestController
@RequestMapping("/api/notifications")
public class SseController {

    // userId -> emitter (실무에선 다중 서버 고려해 Redis Pub/Sub 등 필요)
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam Long userId) {
        // 타임아웃 길게 (예: 60분). 0L 또는 Long.MAX_VALUE면 무제한
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);

        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        // 연결 직후 더미 이벤트(프록시가 빈 응답을 끊지 않도록)
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            emitters.remove(userId);
        }
        return emitter;
    }

    // 이벤트 발생 시 호출 → 해당 유저에게 push
    public void sendNotification(Long userId, Notification n) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .id(String.valueOf(n.getId()))
                        .data(n));
            } catch (IOException e) {
                emitters.remove(userId); // 끊긴 연결 정리
            }
        }
    }
}
```

### WebFlux(리액티브) 버전 — 더 간결

```java
@GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<PriceUpdate> streamPrices() {
    return Flux.interval(Duration.ofSeconds(1))
               .map(tick -> priceService.currentPrice()); // 1초마다 push
}
```

---

## 5.5 SSE의 현실적인 주의점 ⚠️

1. **HTTP/1.1에서는 브라우저 연결 수 제한**(도메인당 ~6개)에 걸림.
   - SSE 연결이 그 한도를 잡아먹음 → 탭 여러 개면 문제.
   - **HTTP/2 사용 시 멀티플렉싱**으로 이 문제 대폭 완화. (SSE는 HTTP/2와 궁합이 좋음)
2. **프록시/로드밸런서 버퍼링**: Nginx 등이 응답을 버퍼링하면 실시간성이 깨짐.
   - `X-Accel-Buffering: no` 헤더, `proxy_buffering off` 설정 필요.
3. **다중 서버(스케일아웃)**: 사용자가 A서버에 연결됐는데 이벤트가 B서버에서 발생하면?
   - → **Redis Pub/Sub**, Kafka 등으로 서버 간 이벤트를 fan-out 해야 함.
4. **유휴 연결 끊김 방지**: 주기적 heartbeat(주석 `:ping`) 전송 권장.

---

## 5.6 SSE vs WebSocket — 언제 SSE?

| 항목 | SSE | WebSocket |
|------|-----|-----------|
| 방향 | 서버→클라 **단방향** | **양방향** |
| 프로토콜 | HTTP (그대로) | ws:// (HTTP 업그레이드) |
| 자동 재연결 | 내장(EventSource) | 직접 구현 |
| 데이터 | 텍스트만 | 텍스트+바이너리 |
| 구현 난이도 | 쉬움 | 상대적으로 복잡 |
| 적합 사례 | 알림, 피드, 시세, 진행률, LLM 토큰 스트리밍 | 채팅, 게임, 협업 편집 |

> 💡 결론: **"서버가 보내기만 하면 되는" 단방향이면 거의 항상 SSE가 정답.**
> 굳이 양방향 WebSocket의 복잡도를 떠안을 필요가 없습니다.
> ChatGPT 같은 LLM 응답 스트리밍도 대부분 SSE를 씁니다.

---

## 핵심 요약

- **SSE**: 하나의 HTTP 연결로 **서버→클라이언트 단방향** 스트리밍. `text/event-stream`.
- 브라우저 `EventSource`가 **자동 재연결 + Last-Event-ID 이어받기** 제공.
- Spring은 `SseEmitter`(MVC) 또는 `Flux`(WebFlux)로 구현.
- 단방향이면 WebSocket보다 단순한 SSE가 보통 더 좋은 선택.
- 운영 시 **프록시 버퍼링, 다중 서버 fan-out(Redis Pub/Sub), heartbeat**를 신경 써야 함.

## 면접/실무 체크리스트

- [ ] SSE와 WebSocket의 가장 큰 차이는? (단방향 vs 양방향, HTTP vs 별도 프로토콜)
- [ ] SSE의 자동 재연결과 Last-Event-ID는 무엇을 해결하나?
- [ ] 다중 서버 환경에서 SSE 이벤트를 어떻게 전 사용자에게 전달하나? (Redis Pub/Sub 등)
- [ ] LLM 토큰 스트리밍에 SSE가 적합한 이유는?

---

이전 → [04. Polling](./04-polling.md) | 다음 → [06. 실시간 통신 ③ WebSocket](./06-websocket.md)
