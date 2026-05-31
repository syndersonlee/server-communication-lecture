# 서버 통신 프로토콜 완전 정복 (Backend / Spring · Java)

> 백엔드 개발자가 알아야 할 "서버와 서버", "클라이언트와 서버" 사이의 모든 통신 기법을
> 바닥(소켓)부터 실시간(WebSocket), 비동기(Kafka)까지 한 번에 정리한 강의 자료입니다.

---

## 대상 독자

- Spring / Java 기반 백엔드 개발자
- HTTP는 써봤지만 "HTTP/2, WebSocket, SSE, gRPC가 정확히 뭐가 다른지" 설명하기 어려운 분
- 실시간 기능(채팅, 알림, 가격 변동, 예약 현황)을 어떤 기술로 구현할지 고민하는 분

## 학습 목표

이 강의를 마치면 다음을 할 수 있습니다.

1. OSI 7계층 / TCP-IP 4계층 위에서 각 프로토콜이 어디에 위치하는지 설명할 수 있다.
2. HTTP/1.0 → 1.1 → 2 → 3의 진화 과정과 각 버전의 성능 특성을 설명할 수 있다.
3. 실시간 통신 기법(Polling, Long Polling, SSE, WebSocket)을 비교하고 상황에 맞게 선택할 수 있다.
4. gRPC, GraphQL, 메시지 큐(Kafka/RabbitMQ)를 언제 써야 하는지 판단할 수 있다.
5. Spring에서 각 기술을 실제로 구현하는 코드를 작성할 수 있다.

---

## 전체 목차 (예상 소요 시간: 약 2시간 20분)

| #  | 챕터 | 핵심 키워드 | 소요(분) |
|----|------|-------------|:--------:|
| 00 | [오리엔테이션 (본 문서)](./README.md) | 전체 지도 | 5 |
| 01 | [네트워크 기초](./01-network-fundamentals.md) | OSI, TCP/UDP, Socket, 3-way handshake | 20 |
| 02 | [HTTP 완전 분석](./02-http.md) | 요청/응답, 메서드, 상태코드, HTTP/0.9~3 | 30 |
| 03 | [HTTPS와 TLS](./03-https-tls.md) | 대칭/비대칭키, 인증서, 핸드셰이크 | 15 |
| 04 | [실시간 통신 ① Polling](./04-polling.md) | Short Polling, Long Polling | 10 |
| 05 | [실시간 통신 ② SSE](./05-sse.md) | Server-Sent Events, SseEmitter | 10 |
| 06 | [실시간 통신 ③ WebSocket](./06-websocket.md) | 핸드셰이크, 프레임, STOMP | 25 |
| 07 | [gRPC](./07-grpc.md) | HTTP/2, Protobuf, 스트리밍 | 15 |
| 08 | [GraphQL](./08-graphql.md) | 스키마, 쿼리, Over/Under-fetching | 10 |
| 09 | [비동기 메시징](./09-messaging.md) | MQ, Kafka, RabbitMQ, AMQP, MQTT | 20 |
| 10 | [WebFlux와 코루틴](./10-webflux-coroutine.md) | 리액티브, Mono/Flux, 코루틴, 가상 스레드 | 20 |
| 11 | [기술 선택 가이드](./11-comparison-and-selection.md) | 비교표, 의사결정 트리, 실전 시나리오 | 10 |

> 🌐 **시각화된 웹 강의 자료**: [`docs/index.html`](./docs/index.html) 을 브라우저로 열면
> 토스 스타일의 시각적인 강의 사이트로 전체 내용을 볼 수 있습니다.
> 🛠 **직접 실행하는 실습**: [`practice/`](./practice) 의 Spring Boot 앱을 띄우면 각 기법을 브라우저에서 호출해볼 수 있습니다.

---

## 한눈에 보는 통신 기법 지도

```
                          [ 통신 방향성 ]
   단방향(요청-응답)                          양방향(실시간)
        │                                          │
   ┌────┴─────┐                          ┌─────────┴──────────┐
   │   HTTP   │                          │  Polling  → 흉내    │
   │  (1.0~3) │                          │  Long Polling → 흉내 │
   │   gRPC   │                          │  SSE → 서버→클라 단방향│
   │ GraphQL  │                          │  WebSocket → 진짜 양방향│
   └──────────┘                          └────────────────────┘

                          [ 호출 시점 ]
   동기 (Sync, 응답을 기다림)                 비동기 (Async, 메시지를 던짐)
        │                                          │
   HTTP / gRPC / GraphQL                  Kafka / RabbitMQ / MQTT
```

## 이 자료를 읽는 법

- 챕터 순서대로 읽는 것을 권장합니다. 뒤 챕터는 앞 챕터 개념(TCP, HTTP)을 전제로 합니다.
- 코드 예제는 모두 **Spring Boot 3.x / Java 17 기준**입니다.
- 각 챕터 끝에는 `핵심 요약`과 `면접/실무 체크리스트`가 있습니다.
- 💡 표시는 실무 팁, ⚠️ 표시는 자주 하는 실수입니다.

---

다음 → [01. 네트워크 기초](./01-network-fundamentals.md)
