# 06. 실시간 통신 ③ WebSocket

> 채팅, 실시간 협업, 게임, 라이브 경매처럼 **양방향**으로 빠르게 주고받아야 한다면 WebSocket입니다.
> "진짜 실시간 양방향 통신"의 표준이죠.

---

## 6.1 WebSocket이란

**WebSocket**: 하나의 TCP 연결 위에서 **양방향(full-duplex)** 통신을 제공하는 프로토콜.
한 번 연결을 맺으면 서버와 클라이언트가 **서로 자유롭게, 언제든** 메시지를 보낼 수 있습니다.

```
       HTTP 핸드셰이크로 연결 → ws:// 프로토콜로 업그레이드
Client ◀══════════════════════════════════════▶ Server
       서버도 보내고, 클라도 보내고 (양방향, 연결 유지)
```

- URL 스킴: `ws://` (평문), `wss://` (TLS 암호화).
- HTTP/1.1의 **Upgrade 메커니즘**으로 연결을 시작한 뒤, 그 TCP 연결을 WebSocket으로 전환.
- 연결 후엔 HTTP 헤더 오버헤드 없이 가벼운 **프레임(frame)** 단위로 통신.

---

## 6.2 핸드셰이크 — HTTP에서 WebSocket으로 업그레이드

WebSocket은 HTTP 요청으로 시작합니다. (그래서 80/443 포트, 방화벽 친화적)

### 클라이언트 요청

```
GET /ws/chat HTTP/1.1
Host: api.yanolja.com
Upgrade: websocket                      ← "프로토콜 바꾸자"
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==   ← 랜덤 키
Sec-WebSocket-Version: 13
```

### 서버 응답 — 101 Switching Protocols

```
HTTP/1.1 101 Switching Protocols        ← ⭐ 이 상태코드가 핵심
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=  ← 키 검증 응답
```

이후부터 그 연결은 더 이상 HTTP가 아니라 **WebSocket 프로토콜**로 통신합니다.

> 💡 02 챕터의 상태코드 표에서 본 "101 Switching Protocols"가 바로 이 순간 등장합니다.

---

## 6.3 WebSocket 프레임

연결 후 데이터는 작은 **프레임** 단위로 오갑니다. (HTTP처럼 매번 큰 헤더를 안 붙임)

- **opcode**: 프레임 종류 (텍스트 0x1, 바이너리 0x2, 종료 0x8, ping 0x9, pong 0xA).
- **payload**: 실제 데이터.
- **masking**: 클라이언트→서버 프레임은 보안상 마스킹 필수.
- **ping/pong**: 연결 생존 확인(heartbeat)용 제어 프레임.

> 텍스트와 **바이너리 모두 전송 가능**한 점이 SSE와의 큰 차이입니다.

---

## 6.4 Spring에서 WebSocket — ① 저수준 핸들러

`spring-boot-starter-websocket` 의존성을 추가합니다.

```java
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session); // 연결됨
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        // 받은 메시지를 모든 세션에 브로드캐스트
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage("broadcast: " + payload));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session); // 연결 종료
    }
}
```

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;

    public WebSocketConfig(ChatWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat")
                .setAllowedOrigins("*"); // 운영에선 도메인 명시!
    }
}
```

---

## 6.5 Spring에서 WebSocket — ② STOMP (권장, 고수준)

저수준 핸들러는 "메시지 라우팅, 구독, 1:N 브로드캐스트"를 직접 다 짜야 합니다.
이를 편하게 해주는 게 **STOMP(Simple Text Oriented Messaging Protocol)** — WebSocket 위에서
동작하는 메시징 서브 프로토콜로, **목적지(destination) 기반 pub/sub** 모델을 제공합니다.

```
구독 모델:
  /topic/room.1   ← 1번 방을 구독한 모두에게 브로드캐스트
  /queue/user.9001 ← 특정 유저에게만
  /app/...         ← 클라가 서버로 메시지 보낼 때
```

### 설정

```java
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")        // 연결 엔드포인트
                .setAllowedOriginPatterns("*")
                .withSockJS();             // WebSocket 미지원 환경 폴백
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue"); // 내장 브로커(구독 경로)
        registry.setApplicationDestinationPrefixes("/app"); // 클라→서버 경로
        // 대규모: enableStompBrokerRelay("/topic","/queue")로 RabbitMQ/ActiveMQ 외부 브로커 연결
    }
}
```

### 메시지 핸들러

```java
@Controller
public class ChatController {

    @MessageMapping("/chat.send/{roomId}")   // 클라가 /app/chat.send/1 로 전송
    @SendTo("/topic/room.{roomId}")          // /topic/room.1 구독자 전체에게
    public ChatMessage send(@DestinationVariable String roomId, ChatMessage msg) {
        return msg; // 반환값이 구독자에게 브로드캐스트됨
    }
}

// 특정 시점에 코드에서 직접 보내기
@Service
public class NotifyService {
    private final SimpMessagingTemplate template;
    public NotifyService(SimpMessagingTemplate template) { this.template = template; }

    public void notifyRoom(String roomId, ChatMessage msg) {
        template.convertAndSend("/topic/room." + roomId, msg);
    }
}
```

### 클라이언트 (stom.js / SockJS)

```javascript
const sock = new SockJS('/ws');
const client = Stomp.over(sock);

client.connect({}, () => {
  // 구독
  client.subscribe('/topic/room.1', (frame) => {
    const msg = JSON.parse(frame.body);
    appendMessage(msg);
  });
  // 전송
  client.send('/app/chat.send/1', {}, JSON.stringify({ text: '안녕하세요' }));
});
```

> 💡 STOMP를 쓰면 구독/브로드캐스트/유저 타겟팅이 선언적으로 해결됩니다.
> 직접 세션 Set을 들고 도는 저수준 코드를 안 짜도 됨.

---

## 6.6 운영 시 핵심 고려사항 ⚠️

1. **스케일아웃(다중 서버)**: 내장 SimpleBroker는 서버 1대 메모리에 구독 정보를 들고 있음.
   - 서버가 여러 대면 A서버 구독자에게 B서버 메시지가 안 감.
   - → **외부 메시지 브로커(RabbitMQ, Redis, ActiveMQ)** 를 STOMP relay로 연결하거나,
     Redis Pub/Sub로 서버 간 메시지를 공유.
2. **동시 연결 수와 스레드 모델**: 연결이 수만~수십만이면 BIO(스레드/연결)는 한계.
   - **Netty 기반 비동기(WebFlux, 또는 전용 게이트웨이)** 로 적은 스레드로 많은 연결 처리.
3. **인증/인가**: 핸드셰이크 시점에 토큰 검증(HandshakeInterceptor), STOMP CONNECT 프레임 헤더로 인증.
4. **heartbeat**: ping/pong 또는 STOMP heartbeat로 죽은 연결 감지/정리.
5. **로드밸런서**: WebSocket 연결을 끊지 않도록 sticky session 또는 L4/L7 설정, idle timeout 늘리기.

---

## 6.7 SSE vs WebSocket vs Long Polling (실시간 3종 정리)

| 항목 | Long Polling | SSE | WebSocket |
|------|-------------|-----|-----------|
| 방향 | 단방향(요청마다) | 서버→클라 단방향 | **양방향** |
| 연결 유지 | 요청마다 재수립 | 1개 유지 | 1개 유지 |
| 프로토콜 | HTTP | HTTP | ws/wss (업그레이드) |
| 바이너리 | △ | X | O |
| 자동 재연결 | 직접 | 내장 | 직접 |
| 오버헤드 | 높음(헤더 반복) | 낮음 | 가장 낮음 |
| 대표 사례 | 레거시 알림 | 알림/피드/시세/LLM | 채팅/게임/협업 |

---

## 핵심 요약

- **WebSocket**: 1개의 TCP 연결로 **양방향 실시간** 통신. `101 Switching Protocols`로 업그레이드.
- 연결 후엔 가벼운 **프레임** 단위 통신, **텍스트+바이너리** 모두 가능.
- Spring은 **저수준 핸들러**와 **STOMP(pub/sub, 권장)** 두 방식 제공.
- 양방향이 꼭 필요할 때만 선택. 단방향이면 SSE가 더 단순.
- 운영의 핵심은 **스케일아웃(외부 브로커/Redis), 동시 연결 처리(비동기), 인증, heartbeat, LB 설정**.

## 면접/실무 체크리스트

- [ ] WebSocket 핸드셰이크에서 등장하는 상태코드와 헤더는? (101, Upgrade/Connection)
- [ ] STOMP가 저수준 WebSocket 대비 제공하는 이점은? (pub/sub, 라우팅, 브로드캐스트)
- [ ] WebSocket을 여러 서버로 확장할 때의 문제와 해결책은? (외부 브로커/Redis Pub/Sub)
- [ ] 단방향이면 WebSocket 대신 무엇을 고려해야 하나? (SSE)

---

이전 → [05. SSE](./05-sse.md) | 다음 → [07. gRPC](./07-grpc.md)
