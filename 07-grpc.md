# 07. gRPC

> MSA(마이크로서비스)에서 "서버 ↔ 서버" 내부 통신을 REST로 하면 느리고 장황합니다.
> 구글이 만든 **gRPC**는 이 영역에서 사실상 표준이 되어가고 있습니다.

---

## 7.1 gRPC란

**gRPC(gRPC Remote Procedure Call)**: 구글이 만든 고성능 **RPC 프레임워크**.
원격 서버의 함수를 마치 로컬 함수처럼 호출합니다.

세 가지 기둥:
1. **HTTP/2** 위에서 동작 → 멀티플렉싱, 스트리밍, 헤더 압축 기본 탑재.
2. **Protocol Buffers(protobuf)** → 바이너리 직렬화. JSON보다 작고 빠름.
3. **IDL(인터페이스 정의 언어)** → `.proto` 파일로 계약을 먼저 정의, 다국어 코드 자동 생성.

```
REST/JSON                          gRPC
─────────────                      ─────────────
텍스트(JSON, 장황)        vs        바이너리(protobuf, 작음)
HTTP/1.1 흔함                       HTTP/2 필수
스키마 느슨(문서/OpenAPI)            엄격한 .proto 계약
브라우저 친화적                      서버 간 통신에 최적
```

---

## 7.2 RPC란? (개념 복습)

**RPC(Remote Procedure Call)**: 네트워크 너머의 함수를 로컬 함수처럼 호출하는 방식.

```java
// 마치 로컬 메서드를 부르는 것처럼...
UserResponse user = userStub.getUser(GetUserRequest.newBuilder().setId(9001).build());
// 실제로는 네트워크 통신이 일어남
```

- REST가 "자원(Resource) 중심"이라면, RPC는 "동작(Action/함수) 중심".
- 직렬화/네트워크/역직렬화를 **스텁(stub)** 이 숨겨줘서 개발자는 함수 호출만 신경 씀.

---

## 7.3 Protocol Buffers — `.proto`로 계약 정의

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.yanolja.grpc.user";

// 서비스(원격 호출 가능한 함수 모음)
service UserService {
  rpc GetUser (GetUserRequest) returns (UserResponse);              // 단일 요청-응답
  rpc ListUsers (ListUsersRequest) returns (stream UserResponse);   // 서버 스트리밍
}

message GetUserRequest {
  int64 id = 1;   // 1, 2는 '필드 번호'(태그). 바이너리 인코딩의 키.
}

message UserResponse {
  int64 id = 1;
  string name = 2;
  string email = 3;
}

message ListUsersRequest {
  int32 page = 1;
}
```

- 이 `.proto`로부터 Java/Go/Python 등 **각 언어의 코드가 자동 생성**됩니다.
- 필드 번호(`= 1`)는 바이너리 포맷의 키 역할. **한 번 정하면 바꾸면 안 됨** (호환성).
- 필드 추가/삭제 시 번호 규칙만 지키면 **하위 호환** 유지 → 점진적 배포에 유리.

---

## 7.4 4가지 통신 방식 ⭐

gRPC는 HTTP/2 스트리밍 덕분에 4가지 패턴을 지원합니다.

```
① Unary (단일)            client ── req ──▶ server ── res ──▶ client
② Server Streaming        client ── req ──▶ server ══ res 여러 개 ══▶
③ Client Streaming        client ══ req 여러 개 ══▶ server ── res ──▶
④ Bidirectional Streaming client ══════ 양방향 동시 스트림 ══════▶ server
```

- ① **Unary**: 일반 요청-응답 (REST와 유사).
- ② **Server Streaming**: 결과를 나눠서 계속 보냄 (대용량 목록, 실시간 피드).
- ③ **Client Streaming**: 클라가 데이터를 여러 번 보내고 마지막에 응답 (파일 업로드, 집계).
- ④ **Bidirectional**: WebSocket처럼 양방향 동시 스트리밍 (실시간 양방향).

---

## 7.5 Spring에서 gRPC

Spring Boot에선 보통 `grpc-spring-boot-starter`(커뮤니티) 또는 Spring 공식 gRPC 지원을 사용합니다.

### 서버 구현

```java
@GrpcService   // grpc-spring-boot-starter 제공
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        UserResponse response = UserResponse.newBuilder()
                .setId(request.getId())
                .setName("홍길동")
                .setEmail("hong@yanolja.com")
                .build();

        responseObserver.onNext(response);   // 응답 전송
        responseObserver.onCompleted();      // 스트림 종료
    }

    // 서버 스트리밍
    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<UserResponse> obs) {
        for (int i = 0; i < 10; i++) {
            obs.onNext(UserResponse.newBuilder().setId(i).setName("user" + i).build());
        }
        obs.onCompleted();
    }
}
```

### 클라이언트 호출

```java
@Service
public class UserClient {

    @GrpcClient("user-service")  // 설정의 채널 이름
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public String getUserName(long id) {
        UserResponse res = userStub.getUser(
                GetUserRequest.newBuilder().setId(id).build());
        return res.getName();  // 원격 호출이지만 로컬 함수처럼!
    }
}
```

---

## 7.6 장단점과 한계

### 장점
- **고성능**: 바이너리 직렬화 + HTTP/2 멀티플렉싱 → REST/JSON 대비 빠르고 가벼움.
- **엄격한 계약**: `.proto`가 단일 진실 공급원. 클라/서버 타입 불일치를 컴파일 단계에서 차단.
- **다국어 지원**: 한 `.proto`로 여러 언어 코드 생성 → polyglot MSA에 적합.
- **스트리밍**: 양방향/서버/클라 스트리밍 네이티브 지원.

### 단점/한계 ⚠️
- **브라우저에서 직접 호출 불가**: 브라우저가 HTTP/2 프레임을 raw로 제어 못 함.
  - → **gRPC-Web** + 프록시(Envoy 등) 필요. 그래서 보통 **외부 공개 API는 REST, 내부는 gRPC**.
- **사람이 읽기 어려움**: 바이너리라 디버깅/로깅이 JSON보다 불편 (grpcurl 등 도구 필요).
- **러닝커브**: protobuf, 코드 생성, HTTP/2 인프라 이해 필요.

---

## 7.7 언제 gRPC를 쓰나

| 상황 | 추천 |
|------|------|
| MSA 내부 서버 간 통신, 고성능/저지연 필요 | **gRPC** |
| polyglot(여러 언어) 서비스 간 엄격한 계약 | **gRPC** |
| 브라우저/모바일 공개 API, 단순 CRUD | REST |
| 실시간 양방향(브라우저) | WebSocket |
| 서버→클라 단방향 스트리밍(브라우저) | SSE |

> 💡 야놀자 같은 대형 서비스의 전형: **외부(앱/웹)는 REST(+그래프QL), 내부 MSA 간은 gRPC.**

---

## 핵심 요약

- **gRPC = HTTP/2 + Protocol Buffers + IDL(.proto)** 기반 고성능 RPC 프레임워크.
- 원격 함수를 로컬처럼 호출. `.proto` 계약에서 다국어 코드 자동 생성.
- **4가지 통신 방식**(Unary / 서버·클라 스트리밍 / 양방향)을 지원.
- 바이너리라 빠르지만 **브라우저 직접 호출 불가(gRPC-Web 필요)**, 디버깅 불편.
- **내부 서버 간 통신**에 최적, 외부 공개 API는 보통 REST 병행.

## 면접/실무 체크리스트

- [ ] gRPC가 빠른 이유 2가지는? (protobuf 바이너리, HTTP/2 멀티플렉싱)
- [ ] REST와 gRPC의 근본 철학 차이는? (자원 중심 vs 함수/동작 중심)
- [ ] gRPC를 브라우저에서 바로 못 쓰는 이유와 해법은? (HTTP/2 raw 제어 불가 → gRPC-Web)
- [ ] gRPC의 4가지 통신 방식은?

---

이전 → [06. WebSocket](./06-websocket.md) | 다음 → [08. GraphQL](./08-graphql.md)
