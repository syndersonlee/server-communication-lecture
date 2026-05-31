# 02. HTTP 완전 분석

> 백엔드 개발자가 가장 많이 다루는 프로토콜. 하지만 "HTTP/2가 1.1보다 왜 빠른가",
> "HTTP는 왜 무상태(stateless)인가"를 정확히 답할 수 있는 사람은 의외로 적습니다.

---

## 2.1 HTTP란 무엇인가

**HTTP(HyperText Transfer Protocol)**: 웹에서 자원(HTML, JSON, 이미지 등)을 주고받기 위한
애플리케이션 계층 프로토콜. 기본적으로 **TCP** 위에서 동작합니다 (HTTP/3 제외).

### 두 가지 핵심 특징

1. **요청-응답(Request-Response) 모델**
   - 클라이언트가 요청해야 서버가 응답한다. 서버가 먼저 말을 걸 수 없다.
   - → 이 한계 때문에 실시간 통신에서 Polling/SSE/WebSocket이 등장 (04~06 챕터).

2. **무상태(Stateless)**
   - 각 요청은 독립적이다. 서버는 이전 요청을 기억하지 않는다.
   - 장점: 서버 확장(scale-out)이 쉬움. 어느 서버가 받아도 됨.
   - 단점: 로그인 상태 유지 같은 게 안 됨 → **쿠키/세션/토큰(JWT)** 으로 상태를 별도 관리.

> 💡 "HTTP는 무상태인데 어떻게 로그인이 유지되나?"
> → 상태를 HTTP 자체가 아니라 쿠키(클라)/세션 저장소(서버)/JWT로 따로 관리하기 때문.

---

## 2.2 HTTP 메시지 구조

### 요청(Request)

```
POST /api/v1/reservations HTTP/1.1      ← ① 시작줄 (메서드 + 경로 + 버전)
Host: api.yanolja.com                   ┐
Content-Type: application/json          ├ ② 헤더(Headers)
Authorization: Bearer eyJhbGci...       │
Content-Length: 51                      ┘
                                        ← ③ 빈 줄 (헤더와 바디 구분)
{ "hotelId": 123, "checkIn": "2026-06-01" }  ← ④ 바디(Body)
```

### 응답(Response)

```
HTTP/1.1 201 Created                    ← ① 상태줄 (버전 + 상태코드 + 메시지)
Content-Type: application/json          ┐
Location: /api/v1/reservations/9001     ├ ② 헤더
Content-Length: 38                      ┘
                                        ← ③ 빈 줄
{ "reservationId": 9001, "status": "OK" }    ← ④ 바디
```

---

## 2.3 HTTP 메서드

| 메서드 | 용도 | 멱등성 | 안전성 | 바디 |
|--------|------|:------:|:------:|:----:|
| GET | 조회 | O | O | X(원칙) |
| POST | 생성, 처리 요청 | X | X | O |
| PUT | 전체 수정/생성 | O | X | O |
| PATCH | 부분 수정 | X | X | O |
| DELETE | 삭제 | O | X | △ |
| HEAD | 헤더만 조회 | O | O | X |
| OPTIONS | 지원 메서드/CORS 확인 | O | O | X |

- **안전성(Safe)**: 서버 상태를 바꾸지 않음 (조회만).
- **멱등성(Idempotent)**: 여러 번 호출해도 결과가 같음.
  - `DELETE /users/1`을 100번 호출 → 결과는 "1번 유저 없음"으로 동일 → 멱등.
  - `POST`로 주문 100번 → 주문 100개 생성 → 멱등 아님.

> 💡 네트워크 재시도(retry) 로직을 짤 때 멱등성이 핵심입니다.
> 멱등하지 않은 POST를 무지성 재시도하면 중복 결제가 날 수 있어요. → 멱등키(Idempotency-Key) 패턴 사용.

---

## 2.4 상태 코드(Status Code)

| 범위 | 의미 | 대표 예시 |
|------|------|-----------|
| 1xx | 정보 | 100 Continue, 101 Switching Protocols(← WebSocket!) |
| 2xx | 성공 | 200 OK, 201 Created, 204 No Content |
| 3xx | 리다이렉션 | 301 Moved Permanently, 304 Not Modified(캐시) |
| 4xx | 클라이언트 오류 | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 429 Too Many Requests |
| 5xx | 서버 오류 | 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout |

> ⚠️ 자주 헷갈리는 것
> - **401 vs 403**: 401은 "너 누구야?(인증 안 됨)", 403은 "너인 건 아는데 권한 없어".
> - **502 vs 504**: 502는 게이트웨이가 잘못된 응답 받음, 504는 응답을 못 받고 타임아웃.

---

## 2.5 주요 헤더

```
[일반/요청]
Host: api.yanolja.com           ← 어느 가상호스트로 갈지 (HTTP/1.1 필수)
User-Agent: ...                 ← 클라이언트 정보
Accept: application/json        ← 받고 싶은 형식
Authorization: Bearer <token>   ← 인증 정보
Cookie: SESSIONID=...           ← 쿠키

[응답]
Content-Type: application/json  ← 바디 형식 (MIME 타입)
Content-Length: 128             ← 바디 길이
Set-Cookie: SESSIONID=...       ← 쿠키 설정
Cache-Control: max-age=3600     ← 캐시 정책
ETag: "abc123"                  ← 캐시 검증용 식별자
Location: /resources/9001       ← 리다이렉트/생성된 자원 위치
```

---

## 2.6 HTTP 버전의 진화 ⭐ (이 챕터의 핵심)

### HTTP/0.9 (1991)
- 단 한 줄: `GET /index.html`. 메서드는 GET뿐, 헤더도 상태코드도 없음. HTML만 전송.

### HTTP/1.0 (1996)
- 헤더, 상태코드, POST 등 도입.
- ⚠️ **치명적 단점**: 요청 1개당 TCP 연결 1개를 맺고 끊음.
  - 이미지 10개 = 3-way handshake 10번. 너무 비쌈.

### HTTP/1.1 (1997) — 가장 오래 쓰인 버전

개선점:
1. **Keep-Alive (Persistent Connection)**: TCP 연결을 재사용. 매번 핸드셰이크 안 함.
2. **파이프라이닝(Pipelining)**: 응답을 안 기다리고 요청 여러 개 연속 전송 (실제론 거의 안 쓰임).
3. **Host 헤더 필수**: 한 IP에 여러 도메인(가상 호스팅) 가능.
4. **청크 전송(Chunked Transfer)**: `Content-Length`를 모를 때 나눠서 전송.

⚠️ **남은 한계 — HOL Blocking (Head-of-Line Blocking)**
- 한 연결에서 요청을 보내면 **응답이 보낸 순서대로** 와야 함.
- 앞 요청(느린 응답)이 막히면 뒤 요청들이 다 기다림.
- 브라우저는 이를 회피하려고 도메인당 커넥션을 6개씩 열었음 (비효율).

```
[HTTP/1.1의 HOL Blocking]
연결1: [요청A]───(A 느림, 대기...)───[응답A][응답B][응답C]
                  └ B, C는 준비됐어도 A 끝날 때까지 못 감
```

### HTTP/2 (2015) — 성능 혁명

핵심 키워드: **하나의 TCP 연결에서 병렬 처리**

1. **바이너리 프레이밍(Binary Framing)**
   - 텍스트가 아닌 바이너리 프레임 단위로 쪼개서 전송. 파싱 빠르고 효율적.
2. **멀티플렉싱(Multiplexing)** ⭐
   - 하나의 TCP 연결 위에서 여러 요청/응답을 **스트림**으로 동시에 주고받음.
   - HTTP 레벨의 HOL Blocking 해결. 도메인당 커넥션 6개 트릭 불필요.
3. **헤더 압축(HPACK)**
   - 반복되는 헤더(쿠키 등)를 압축. 1.1은 매 요청 헤더를 그대로 보냈음.
4. **서버 푸시(Server Push)**
   - 요청 전에 서버가 미리 리소스를 보냄 (현재는 거의 폐기/비권장).
5. **스트림 우선순위**: 중요한 리소스 먼저.

```
[HTTP/2의 멀티플렉싱]
연결1 (하나의 TCP): 
   스트림1: [A1][A2]      ┐
   스트림2: [B1]          ├ 동시에 뒤섞여 전송, 받는 쪽이 스트림ID로 재조립
   스트림3: [C1][C2][C3]  ┘
```

⚠️ **HTTP/2의 남은 한계 — TCP 레벨 HOL Blocking**
- HTTP 레벨은 해결했지만, **TCP 자체가** 패킷 1개 유실 시 그 뒤 전부를 막음.
- 멀티플렉싱된 모든 스트림이 TCP 재전송을 기다림. → 이걸 HTTP/3가 해결.

### HTTP/3 (2022) — TCP를 버리다

- **QUIC** 프로토콜 위에서 동작. QUIC은 **UDP** 기반 + 신뢰성/암호화를 자체 구현.
- **TCP HOL Blocking 해결**: 스트림별로 독립적이라 한 스트림 패킷 유실이 다른 스트림을 막지 않음.
- **연결 수립 빠름**: TCP+TLS 핸드셰이크(2~3 RTT)를 QUIC은 1 RTT(또는 0-RTT)로.
- **Connection Migration**: Wi-Fi ↔ LTE 전환 시에도 연결 유지 (IP가 바뀌어도 Connection ID로 식별).

### 버전 비교 요약표

| 항목 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|------|----------|--------|--------|
| 전송 계층 | TCP | TCP | **QUIC(UDP)** |
| 포맷 | 텍스트 | 바이너리 | 바이너리 |
| 멀티플렉싱 | X (연결 여러 개로 회피) | O | O |
| HOL Blocking | HTTP+TCP 모두 발생 | TCP 레벨 잔존 | 해결 |
| 헤더 압축 | X | HPACK | QPACK |
| 암호화 | 선택(HTTPS) | 사실상 필수 | 항상 |

---

## 2.7 Spring에서의 HTTP

### 서버: REST 컨트롤러

```java
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@RequestBody @Valid ReservationRequest req) {
        ReservationResponse created = service.create(req);
        return ResponseEntity
                .created(URI.create("/api/v1/reservations/" + created.id())) // 201 + Location
                .body(created);
    }
}
```

### 클라이언트 ① RestTemplate (동기, 레거시 — 유지보수 모드)

```java
RestTemplate restTemplate = new RestTemplate();
ReservationResponse res = restTemplate.getForObject(
        "https://api.yanolja.com/api/v1/reservations/9001",
        ReservationResponse.class);
```

### 클라이언트 ② WebClient (비동기/논블로킹, 현재 권장)

```java
WebClient client = WebClient.builder()
        .baseUrl("https://api.yanolja.com")
        .build();

Mono<ReservationResponse> mono = client.get()
        .uri("/api/v1/reservations/{id}", 9001)
        .retrieve()
        .bodyToMono(ReservationResponse.class);

// 블로킹 없이 구독; 동기 필요 시 .block()
mono.subscribe(res -> log.info("응답: {}", res));
```

### 클라이언트 ③ RestClient (Spring 6.1+, 동기지만 모던한 API)

```java
RestClient restClient = RestClient.create("https://api.yanolja.com");

ReservationResponse res = restClient.get()
        .uri("/api/v1/reservations/{id}", 9001)
        .retrieve()
        .body(ReservationResponse.class);
```

> 💡 정리: 신규 코드라면 **논블로킹 필요 시 WebClient**, **단순 동기 호출은 RestClient**.
> RestTemplate는 신규 사용 비권장(maintenance mode).

---

## 핵심 요약

- HTTP는 **요청-응답 + 무상태** 모델. 서버가 먼저 말 못 거는 한계가 실시간 기술들을 낳았다.
- 메서드의 **안전성/멱등성**은 재시도 설계에 직결된다.
- 버전 진화의 한 줄 요약:
  - **1.1**: 연결 재사용했지만 HOL Blocking.
  - **2**: 멀티플렉싱으로 HTTP HOL 해결, 단 TCP HOL 잔존.
  - **3**: QUIC(UDP)로 TCP HOL까지 해결, 연결도 빠름.
- Spring 클라이언트는 **WebClient / RestClient** 권장.

## 면접/실무 체크리스트

- [ ] HTTP가 무상태인데 로그인은 어떻게 유지하나? (쿠키/세션/JWT)
- [ ] HTTP/1.1의 HOL Blocking과 HTTP/2가 이를 해결한 방법은? (멀티플렉싱)
- [ ] HTTP/2도 못 푼 한계와 HTTP/3의 해법은? (TCP HOL → QUIC/UDP)
- [ ] 멱등성이 재시도 로직에서 왜 중요한가? (중복 결제 방지, 멱등키)
- [ ] 401과 403의 차이는?

---

이전 → [01. 네트워크 기초](./01-network-fundamentals.md) | 다음 → [03. HTTPS와 TLS](./03-https-tls.md)
