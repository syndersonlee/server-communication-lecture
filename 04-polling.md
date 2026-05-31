# 04. 실시간 통신 ① Polling

> "서버는 먼저 말을 걸 수 없다"는 HTTP의 한계를 떠올려 봅시다.
> 그렇다면 "새 알림이 왔는지" 어떻게 알까요? 가장 단순한 답이 **폴링(Polling)** 입니다.

---

## 4.1 실시간 통신이라는 숙제

예약 현황, 채팅, 알림, 실시간 가격 변동... 이런 기능은 "서버에 새 데이터가 생기면
클라이언트가 즉시 알아야" 합니다. 하지만 HTTP는 클라이언트가 물어봐야만 답합니다.

이 숙제를 푸는 4가지 단계적 접근이 있습니다.

```
Short Polling  →  Long Polling  →  SSE  →  WebSocket
(주기적 질문)     (질문 후 대기)    (서버→클라 단방향)  (완전 양방향)
   비효율 ────────────────────────────────▶ 효율적/실시간
```

이번 챕터는 앞의 두 가지, 다음 챕터들에서 SSE(05)와 WebSocket(06)을 다룹니다.

---

## 4.2 Short Polling (짧은 폴링)

**아이디어**: 클라이언트가 일정 주기(예: 3초)마다 서버에 "새 거 있어?"라고 물어봄.

```
Client                         Server
  │── 새 알림 있어? ──────────▶ │
  │◀─ 없어 ────────────────────│   (3초 대기)
  │── 새 알림 있어? ──────────▶ │
  │◀─ 없어 ────────────────────│   (3초 대기)
  │── 새 알림 있어? ──────────▶ │
  │◀─ 있어! {...} ─────────────│
```

### 장점
- 구현이 매우 단순. 그냥 평범한 HTTP API를 주기적으로 호출.
- 모든 환경에서 동작 (특별한 기술 불필요).

### 단점
- ⚠️ **낭비가 심하다**: 데이터가 없어도 계속 요청 → 대부분 "없어" 응답. 트래픽/서버 부하.
- **실시간성 낮음**: 주기가 3초면 최대 3초 지연.
- 사용자 수 × 빈도 만큼 요청 폭증. (유저 1만 명 × 3초 주기 = 초당 3천 건)

### 클라이언트 예시 (JS)
```javascript
setInterval(async () => {
  const res = await fetch('/api/notifications/new');
  const data = await res.json();
  if (data.length > 0) render(data);
}, 3000); // 3초마다
```

### 언제 쓰나
- 실시간성이 별로 안 중요하고, 데이터 변경이 잦지 않은 경우.
- 예: "배포 상태 확인", "백그라운드 배치 작업 진행률"을 가끔 갱신.

---

## 4.3 Long Polling (롱 폴링)

**아이디어**: 클라이언트가 요청을 보내면, 서버는 **새 데이터가 생길 때까지 응답을 보류(hold)**.
데이터가 생기면 그때 응답하고, 클라이언트는 즉시 다시 요청.

```
Client                         Server
  │── 새 알림 있어? ──────────▶ │
  │                            │  (응답 안 함, 대기...)
  │          ...               │  (이벤트 발생 대기)
  │◀─ 있어! {...} ─────────────│  ← 데이터 생기자마자 응답
  │── (즉시) 새 알림 있어? ────▶ │
  │                            │  (다시 대기...)
```

### 장점
- Short Polling보다 **실시간성 좋음** (이벤트 발생 즉시 전달).
- 빈 응답이 줄어 **불필요한 트래픽 감소**.

### 단점
- 서버가 다수 요청을 "보류" 상태로 들고 있어야 함 → **연결/스레드 점유**.
  - (Servlet의 비동기 처리, `DeferredResult`로 스레드 점유는 완화 가능)
- 타임아웃 관리 필요 (무한정 못 기다리니 30~60초 후 빈 응답 → 재요청).
- 여전히 매번 새 HTTP 요청을 맺음 (헤더 오버헤드).

### Spring 예시 — DeferredResult로 Long Polling

```java
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    // 대기 중인 요청들을 모아둠 (userId -> 보류된 응답)
    private final Map<Long, DeferredResult<List<Notification>>> waiting = new ConcurrentHashMap<>();

    @GetMapping("/long-poll")
    public DeferredResult<List<Notification>> longPoll(@RequestParam Long userId) {
        // 30초 후 타임아웃되면 빈 리스트 응답 → 클라가 재요청
        DeferredResult<List<Notification>> result =
                new DeferredResult<>(30_000L, Collections.emptyList());

        waiting.put(userId, result);
        result.onCompletion(() -> waiting.remove(userId));
        return result; // 톰캣 스레드는 즉시 반납, 요청은 보류 상태
    }

    // 다른 곳(이벤트 발생 시)에서 호출 → 보류된 요청에 응답
    public void pushNotification(Long userId, Notification n) {
        DeferredResult<List<Notification>> result = waiting.get(userId);
        if (result != null) {
            result.setResult(List.of(n)); // 즉시 응답 전송
        }
    }
}
```

> 💡 `DeferredResult`의 핵심: 요청을 보류해도 **톰캣 워커 스레드를 점유하지 않는다**.
> 콜백 기반이라 스레드 고갈을 막아줌. (Servlet 3.0 비동기)

### 언제 쓰나
- WebSocket/SSE를 쓸 수 없는 제약 환경(구형 브라우저, 특정 프록시).
- 과거 많은 채팅/알림 서비스가 사용. 지금은 SSE/WebSocket로 많이 대체됨.

---

## 4.4 Short vs Long Polling 비교

| 항목 | Short Polling | Long Polling |
|------|---------------|--------------|
| 요청 빈도 | 주기적(고정) | 응답 받은 직후 |
| 실시간성 | 낮음(주기만큼 지연) | 비교적 높음 |
| 빈 응답 낭비 | 많음 | 적음 |
| 서버 부담 | 요청 처리량 ↑ | 연결 보류 관리 |
| 구현 난이도 | 매우 쉬움 | 중간 |

---

## 핵심 요약

- HTTP의 "서버가 먼저 못 보냄" 한계를 우회하려는 첫 시도가 **Polling**.
- **Short Polling**: 주기적 질문. 단순하지만 낭비 큼.
- **Long Polling**: 응답을 보류했다가 이벤트 발생 시 전달. 실시간성↑, 트래픽↓.
- Spring에선 `DeferredResult`로 스레드 점유 없이 Long Polling 구현 가능.
- 지금은 대부분 더 효율적인 **SSE(05)** 나 **WebSocket(06)** 으로 대체.

## 면접/실무 체크리스트

- [ ] Short vs Long Polling의 차이와 트레이드오프는?
- [ ] Long Polling에서 `DeferredResult`가 해결하는 문제는? (스레드 점유)
- [ ] Polling이 비효율적인 근본 이유는? (단방향 요청-응답의 반복)

---

이전 → [03. HTTPS와 TLS](./03-https-tls.md) | 다음 → [05. 실시간 통신 ② SSE](./05-sse.md)
