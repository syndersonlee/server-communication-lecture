# 통신 프로토콜 실습 (Spring Boot)

[강의 문서(00~10장)](../README.md)와 함께 보는 **인터랙티브 실습 환경**입니다.
브라우저에서 REST · Polling · SSE · WebSocket을 직접 호출하며 동작을 눈으로 확인할 수 있습니다.

## 요구 사항

- **JDK 17 이상** (빌드는 Gradle toolchain이 JDK 21을 자동 사용. 없으면 자동 다운로드)
- 인터넷 연결 (최초 1회 의존성/JDK 다운로드 + WebSocket용 CDN)

## 실행

```bash
cd practice
./gradlew bootRun
```

서버가 뜨면 브라우저에서 접속:

```
http://localhost:8080
```

> 빌드만 하려면 `./gradlew build`, 실행 가능한 jar는 `build/libs/`에 생성됩니다.

## 실습 메뉴

| 메뉴 | 경로 | 백엔드 |
|------|------|--------|
| 1. HTTP / REST | `/rest.html` | `RestDemoController` |
| 2. Short Polling | `/short-polling.html` | `PollingController` |
| 3. Long Polling | `/long-polling.html` | `PollingController` (DeferredResult) |
| 4. SSE | `/sse.html` | `SseController` (SseEmitter) |
| 5. WebSocket | `/websocket.html` | `ChatController` (STOMP) |
| 6~8. gRPC/GraphQL/메시징 | `/grpc.html` 등 | 개념·코드 페이지 |

## 추천 실습 순서

1. **REST**로 요청-응답·상태코드·멱등성 확인
2. **Short → Long Polling**으로 실시간 흉내내기의 한계와 개선 체감
3. **SSE**(단방향) → **WebSocket**(양방향) 비교
4. **gRPC/GraphQL/메시징** 개념 페이지로 큰 그림 완성

> 💡 SSE/WebSocket은 **이 페이지를 여러 탭에 띄워** 브로드캐스트를 확인하면 효과적입니다.

## 프로젝트 구조

```
practice/
├─ build.gradle / settings.gradle
└─ src/main/
   ├─ java/com/yanolja/practice/
   │  ├─ PracticeApplication.java
   │  ├─ config/WebSocketConfig.java
   │  ├─ web/RestDemoController.java
   │  ├─ web/PollingController.java
   │  ├─ web/SseController.java
   │  └─ chat/ChatController.java
   └─ resources/
      ├─ application.yml
      └─ static/   (실습 웹사이트: HTML/CSS/JS)
```
