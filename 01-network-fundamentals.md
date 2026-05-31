# 01. 네트워크 기초

> "HTTP는 TCP 위에서 돈다"는 말을 자주 듣지만, 정확히 무슨 뜻일까요?
> 모든 통신 기법을 이해하려면 먼저 그 아래에 깔린 계층 구조를 알아야 합니다.

---

## 1.1 왜 계층(Layer)으로 나누는가

네트워크 통신은 "한국에 있는 내 노트북의 크롬"에서 "미국 데이터센터의 톰캣 서버"까지
신호를 보내는, 엄청나게 복잡한 작업입니다. 이걸 한 덩어리로 만들면 유지보수가 불가능합니다.

그래서 **역할을 계층으로 분리**합니다. 각 계층은 바로 아래 계층의 기능만 사용하고,
자기 위 계층에는 기능만 제공합니다. (관심사의 분리, SoC)

> 💡 비유: 택배 시스템. 나는 "물건을 보낸다"만 신경 쓰면 되고(애플리케이션),
> 트럭 배차/도로 선택(전송/네트워크)은 택배사가 알아서 합니다.

---

## 1.2 OSI 7계층 vs TCP/IP 4계층

이론(OSI)과 현실(TCP/IP)을 같이 봅니다.

```
OSI 7 Layer                 TCP/IP 4 Layer        대표 프로토콜 / 예시
─────────────────────────────────────────────────────────────────────
7. Application (응용)  ┐
6. Presentation (표현)  ├──▶  Application       HTTP, WebSocket, gRPC, DNS, FTP
5. Session (세션)      ┘                        (우리가 코드로 다루는 영역)
─────────────────────────────────────────────────────────────────────
4. Transport (전송)    ───▶  Transport         TCP, UDP, QUIC
                                               (포트, 신뢰성, 흐름제어)
─────────────────────────────────────────────────────────────────────
3. Network (네트워크)  ───▶  Internet          IP, ICMP
                                               (IP 주소, 라우팅)
─────────────────────────────────────────────────────────────────────
2. Data Link (데이터링크)┐
1. Physical (물리)      ┴──▶  Network Access    Ethernet, Wi-Fi, MAC
─────────────────────────────────────────────────────────────────────
```

**백엔드 개발자가 집중할 곳**: Application 계층과 Transport 계층.
이 강의에서 다루는 HTTP, WebSocket, gRPC는 전부 **Application 계층**이고,
그 아래 **TCP/UDP** 위에서 동작합니다.

### 데이터가 흘러가는 과정 (캡슐화)

내가 `"Hello"`를 보내면 각 계층을 내려가며 헤더가 붙습니다.

```
Application :        [ Hello ]                         ← HTTP 메시지
Transport   :   [ TCP헤더 | Hello ]                    ← 세그먼트 (포트 정보 추가)
Internet    : [ IP헤더 | TCP헤더 | Hello ]              ← 패킷 (IP 주소 추가)
Network     :[Eth | IP헤더 | TCP헤더 | Hello | Eth꼬리] ← 프레임 (MAC 주소 추가)
```

받는 쪽은 역순으로 헤더를 하나씩 벗기며(역캡슐화) 올라갑니다.

---

## 1.3 TCP vs UDP — 전송 계층의 두 주인공

| 구분 | TCP | UDP |
|------|-----|-----|
| 연결 | 연결형 (3-way handshake) | 비연결형 |
| 신뢰성 | 보장 (재전송, 순서보장, 흐름/혼잡제어) | 보장 안 함 (그냥 던짐) |
| 속도 | 상대적으로 느림 | 빠름 |
| 헤더 크기 | 20~60 byte | 8 byte |
| 데이터 단위 | 세그먼트(Segment) / 바이트 스트림 | 데이터그램(Datagram) |
| 사용 예 | HTTP, WebSocket, gRPC, DB 연결 | DNS, 영상 스트리밍, 게임, QUIC(HTTP/3) |

### TCP: 신뢰성이 생명

- **순서 보장**: 패킷에 순번(sequence number)을 매겨 도착 순서가 뒤바뀌어도 재조립.
- **재전송**: 응답(ACK)이 안 오면 다시 보냄.
- **흐름 제어(Flow Control)**: 받는 쪽이 감당할 만큼만 보냄 (window size).
- **혼잡 제어(Congestion Control)**: 네트워크가 막히면 속도를 줄임.

> "TCP는 바이트 스트림(byte stream)"이라는 점이 중요합니다.
> 내가 `send("AB")` `send("CD")`를 두 번 호출해도, 받는 쪽은 `"ABCD"`를 한 번에 받을 수도 있습니다.
> → 이래서 메시지 경계를 직접 정해야 함 (HTTP의 `Content-Length`, 개행 구분자 등).

### UDP: 빠르지만 책임은 네가 져라

- 핸드셰이크 없음 → 지연이 적음.
- 손실/순서 뒤바뀜을 애플리케이션이 알아서 처리.
- 실시간 영상통화처럼 "조금 깨져도 되니 빠른 게 중요한" 경우에 적합.

> 💡 HTTP/3가 UDP 기반(QUIC)으로 바뀐 이유는 06, 02 챕터에서 다룹니다.

---

## 1.4 TCP 3-way Handshake & 4-way Handshake

### 연결 수립 (3-way)

```
Client                          Server
  │                                │
  │  ① SYN (seq=x)                 │   "연결하자"
  │ ─────────────────────────────▶ │
  │                                │
  │  ② SYN+ACK (seq=y, ack=x+1)    │   "그래, 나도 준비됐어"
  │ ◀───────────────────────────── │
  │                                │
  │  ③ ACK (ack=y+1)               │   "좋아, 시작!"
  │ ─────────────────────────────▶ │
  │                                │
  │        === 연결 수립 ===        │
```

### 연결 종료 (4-way)

```
Client                          Server
  │  ① FIN          ─────────────▶ │   "나 다 보냈어"
  │  ② ACK          ◀───────────── │   "알겠어"
  │  ③ FIN          ◀───────────── │   "나도 다 보냈어"
  │  ④ ACK          ─────────────▶ │   "끝!"  (TIME_WAIT 후 종료)
```

> ⚠️ 매 요청마다 이 핸드셰이크를 하면 비싸겠죠? 그래서 HTTP/1.1의 **Keep-Alive**,
> 커넥션 풀(HikariCP, WebClient의 connection pool) 같은 "연결 재사용" 기법이 등장합니다.

---

## 1.5 소켓(Socket) — 통신의 끝점

**소켓 = IP주소 + 포트번호** 의 조합으로 정의되는 통신 끝점(endpoint)입니다.
운영체제가 제공하는 통신 추상화이며, 우리가 쓰는 모든 상위 프로토콜은 결국 소켓 위에서 동작합니다.

- 하나의 TCP 연결 = `(출발지 IP, 출발지 Port, 목적지 IP, 목적지 Port)` 4개로 식별.
- 서버는 보통 **잘 알려진 포트(well-known port)** 로 listen: HTTP 80, HTTPS 443.

### Java의 가장 원시적인 소켓 코드

상위 프레임워크가 내부에서 뭘 하는지 감을 잡기 위한 예시입니다. (실무에선 직접 안 짭니다)

```java
// 서버: 8080 포트로 들어오는 TCP 연결을 받는다
try (ServerSocket serverSocket = new ServerSocket(8080)) {
    while (true) {
        Socket client = serverSocket.accept(); // 연결 올 때까지 블로킹
        try (BufferedReader in = new BufferedReader(
                 new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            String line = in.readLine();
            System.out.println("받은 데이터: " + line);
            out.println("Echo: " + line); // 그대로 돌려줌
        }
    }
}
```

```java
// 클라이언트
try (Socket socket = new Socket("localhost", 8080);
     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
     BufferedReader in = new BufferedReader(
         new InputStreamReader(socket.getInputStream()))) {
    out.println("Hello Server");
    System.out.println("서버 응답: " + in.readLine());
}
```

> 💡 Spring MVC의 톰캣, WebSocket, gRPC 서버 전부 결국 이 `ServerSocket.accept()` 위에서
> 동작합니다. 우리는 그 위 추상화를 쓸 뿐이죠.

### Blocking I/O vs Non-blocking I/O (맛보기)

- **BIO (Blocking)**: 연결 1개당 스레드 1개. 위 예제 방식. 동시 연결 1만 개 → 스레드 1만 개 → 메모리 폭발.
- **NIO (Non-blocking, 멀티플렉싱)**: 적은 스레드로 많은 연결 처리 (Selector). Netty, Spring WebFlux의 기반.

> 이 차이가 나중에 "WebSocket 수만 개 동시 연결을 어떻게 감당하나?"에서 핵심이 됩니다.

---

## 1.6 포트와 도메인, 그리고 DNS

- **DNS(Domain Name System)**: `www.yanolja.com` → `123.45.67.89` 로 변환. (UDP 53번 포트 주로 사용)
- 브라우저가 요청을 보내기 전 순서: `DNS 조회 → TCP 연결 → (TLS 핸드셰이크) → HTTP 요청`.
- 이 "연결 전 준비 비용"을 줄이는 게 성능 최적화의 큰 축입니다.

---

## 핵심 요약

- 통신은 **계층 구조**로 분리되며, 백엔드는 주로 **Application(HTTP 등) + Transport(TCP/UDP)** 를 다룬다.
- **TCP**: 연결형, 신뢰성 보장, 순서/재전송. → HTTP, WebSocket, gRPC의 토대.
- **UDP**: 비연결형, 빠르지만 신뢰성 미보장. → DNS, QUIC(HTTP/3).
- **3-way handshake**로 연결을 맺기 때문에 연결 수립은 비싸다 → 연결 재사용이 중요.
- **소켓 = IP + Port**, 모든 상위 프로토콜의 토대.

## 면접/실무 체크리스트

- [ ] HTTP는 어떤 전송 계층 프로토콜 위에서 동작하는가? (TCP, 단 HTTP/3는 UDP)
- [ ] TCP가 신뢰성을 보장하는 3가지 메커니즘은? (순서번호, 재전송/ACK, 흐름·혼잡제어)
- [ ] 3-way handshake가 매 요청마다 일어나면 왜 비싼가?
- [ ] "TCP는 바이트 스트림"이라는 말의 의미와, 그래서 생기는 문제는?

---

이전 → [00. 오리엔테이션](./README.md) | 다음 → [02. HTTP 완전 분석](./02-http.md)
