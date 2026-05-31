# 10. WebFlux와 코루틴 (리액티브 · 비동기 심화)

> 지금까지는 "어떤 프로토콜로 통신하는가"를 봤습니다. 이번엔 시선을 서버 안쪽으로 돌립니다.
> **"이 통신들을 처리하는 서버의 동시성 모델을 어떻게 가져갈 것인가?"** — Spring MVC(블로킹) vs WebFlux(리액티브) vs 코루틴.

---

## 10.1 출발점: Thread-per-Request의 한계

01장에서 본 **BIO(Blocking I/O)** 를 떠올려 봅시다. 전통적인 Spring MVC(톰캣)는
**요청 1개 = 스레드 1개**로 처리합니다.

```
요청 A ─▶ [스레드1] ─ DB 호출(50ms 블로킹) ─ 외부API(200ms 블로킹) ─▶ 응답
요청 B ─▶ [스레드2] ─ ...
요청 C ─▶ [스레드3] ─ ...
```

문제는 **I/O 대기 시간**입니다. 스레드가 DB나 외부 API 응답을 기다리는 동안
그 스레드는 **아무 일도 안 하면서 점유**됩니다(블로킹).

- 톰캣 기본 스레드 풀은 보통 200개. 동시 요청이 폭증하고 각 요청이 느린 외부 호출을 하면
  → 스레드 풀 고갈 → 새 요청은 큐에서 대기 → 지연 폭발(latency spike).
- 스레드는 비싸다: 각 스레드 스택 ~1MB. 수만 개를 만들 수 없다.

> 💡 핵심 통찰: 대부분의 백엔드는 **CPU가 아니라 I/O 대기**가 병목입니다.
> "노는 스레드"를 줄이면 같은 자원으로 훨씬 많은 동시 요청을 처리할 수 있습니다.

이 문제를 푸는 두 갈래가 **리액티브(WebFlux)** 와 **코루틴**, 그리고 새로 등장한 **가상 스레드**입니다.

---

## 10.2 리액티브 프로그래밍과 Spring WebFlux

### 이벤트 루프 모델

WebFlux는 **Netty** 기반의 **논블로킹(Non-blocking) I/O + 이벤트 루프**로 동작합니다.
(01장의 NIO/멀티플렉싱)

```
적은 수의 이벤트 루프 스레드(보통 CPU 코어 수)가
수많은 요청을 "대기는 콜백으로 넘기고" 번갈아 처리

[이벤트 루프 스레드 4개] ──▶ 요청 수만 개를 논블로킹하게 처리
   └ DB/외부호출은 "응답 오면 알려줘"라고 등록만 하고 다음 일을 함
```

스레드가 I/O를 기다리며 노는 일이 없으므로, **적은 스레드로 높은 동시성**을 달성합니다.

### Project Reactor: Mono와 Flux

WebFlux의 핵심 타입은 두 가지입니다.

- `Mono<T>` : 0~1개의 결과를 비동기로 발행 (단건).
- `Flux<T>` : 0~N개의 결과를 비동기로 발행 (스트림).

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository repository;   // R2DBC (논블로킹 DB)
    private final WebClient webClient;          // 논블로킹 HTTP 클라이언트

    @GetMapping("/{id}")
    public Mono<UserProfile> getProfile(@PathVariable Long id) {
        Mono<User> user = repository.findById(id);                 // 논블로킹 DB
        Mono<Grade> grade = webClient.get()
                .uri("/grades/{id}", id)
                .retrieve()
                .bodyToMono(Grade.class);                          // 논블로킹 외부 호출

        // 두 비동기 결과를 조합 — 어느 스레드도 블로킹하지 않는다
        return Mono.zip(user, grade)
                   .map(tuple -> new UserProfile(tuple.getT1(), tuple.getT2()));
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<User> stream() {
        return repository.findAll();   // 데이터가 준비되는 대로 흘려보냄(스트리밍)
    }
}
```

### 백프레셔(Backpressure) — 리액티브의 진짜 무기

생산자가 소비자보다 빠르면? 데이터가 쌓여 OOM이 날 수 있습니다.
리액티브 스트림은 **소비자가 "나 N개만 더 줘"라고 요청**하는 백프레셔를 표준으로 지원합니다.

```
Publisher ── (소비자가 감당할 만큼만) ──▶ Subscriber
            "request(10)" → 10개만 → 처리 후 다시 "request(10)"
```

> 대용량 스트리밍(파일, 이벤트, DB 커서)을 안전하게 흘려보낼 때 결정적입니다.

⚠️ **WebFlux의 함정**: 파이프라인 어딘가에서 **블로킹 코드(JDBC, 동기 라이브러리)** 를
호출하면 이벤트 루프 스레드가 막혀 **전체 성능이 붕괴**됩니다.
→ WebFlux를 쓰려면 **위에서 아래까지 전부 논블로킹**이어야 합니다 (R2DBC, WebClient 등).

---

## 10.3 Kotlin 코루틴(Coroutine)

리액티브의 성능 이점은 매력적이지만, `Mono`/`Flux` 연산자 체이닝은 **읽기 어렵고
디버깅이 힘듭니다**(스택 트레이스가 의미를 잃음). 코루틴은 이 문제를 해결합니다.

**코루틴 = 논블로킹인데 동기 코드처럼 보이게 쓰는 방식.** (Kotlin)

```kotlin
@RestController
@RequestMapping("/api/users")
class UserController(
    private val repository: UserRepository,   // 코루틴/R2DBC
    private val webClient: WebClient,
) {
    // suspend 함수: 논블로킹이지만 위에서 아래로 읽히는 평범한 코드처럼 보인다
    @GetMapping("/{id}")
    suspend fun getProfile(@PathVariable id: Long): UserProfile = coroutineScope {
        val user = async { repository.findById(id) }      // 동시에 시작
        val grade = async { gradeClient.fetch(id) }       // 동시에 시작
        UserProfile(user.await(), grade.await())          // 둘 다 끝나면 조합
    }

    @GetMapping
    fun stream(): Flow<User> = repository.findAll()       // Flux ↔ Flow
}
```

핵심 개념:
- **`suspend`**: 이 함수는 중간에 "중단(suspend)" 가능. 중단되면 스레드를 반납하고,
  결과가 오면 다시 이어서 실행 → 스레드를 점유하지 않음(논블로킹).
- **구조적 동시성(Structured Concurrency)**: `coroutineScope` 안의 자식 작업들은
  스코프와 생명주기를 함께함 → 누수/취소 누락 방지.
- **`Flow`**: 코루틴 버전의 `Flux`(콜드 스트림, 백프레셔 지원).
- Spring WebFlux는 코루틴을 1급으로 지원: 컨트롤러를 `suspend`로 선언하면 됨.

> 💡 같은 논블로킹이라도, **Reactor는 함수형 선언적 스타일**, **코루틴은 명령형(절차적)
> 스타일**입니다. 코루틴은 "콜백 지옥/연산자 체이닝" 없이 비동기를 직관적으로 표현합니다.

---

## 10.4 ⭐ 게임 체인저: 가상 스레드 (Virtual Threads, Java 21+)

2026년 현재 이 주제를 빼놓을 수 없습니다. **Project Loom의 가상 스레드**는
"블로킹 코드 스타일 그대로 + 높은 동시성"을 가능하게 합니다.

- 가상 스레드는 JVM이 관리하는 **초경량 스레드**. 수십만~수백만 개 생성 가능.
- 블로킹 호출(예: JDBC) 시 **OS 스레드를 점유하지 않고** JVM이 알아서 양보(unmount).
- 즉, **익숙한 동기/블로킹 코드(Spring MVC, JPA)를 그대로 쓰면서** I/O 동시성 문제를 상당 부분 해소.

```yaml
# Spring Boot 3.2+ : 한 줄로 가상 스레드 활성화
spring:
  threads:
    virtual:
      enabled: true
```

> 💡 영향: "높은 동시성 = 무조건 WebFlux"라는 공식이 약해졌습니다.
> **단순히 I/O 동시성만 필요하다면**, 복잡한 리액티브 대신 **MVC + 가상 스레드**가
> 더 쉬운 선택일 때가 많습니다.
> 단, **백프레셔가 필요한 스트리밍**은 여전히 리액티브(WebFlux/코루틴 Flow)의 영역입니다.

---

## 10.5 비교: MVC vs WebFlux vs 코루틴 vs 가상 스레드

| 항목 | Spring MVC | WebFlux(Reactor) | 코루틴(Kotlin) | MVC + 가상 스레드 |
|------|-----------|------------------|----------------|-------------------|
| I/O 모델 | 블로킹 | 논블로킹 | 논블로킹 | 블로킹(but 경량) |
| 동시성 | 스레드 풀 한계 | 매우 높음 | 매우 높음 | 매우 높음 |
| 코드 스타일 | 명령형(쉬움) | 선언적(어려움) | 명령형(쉬움) | 명령형(쉬움) |
| 백프레셔 | X | **O** | **O(Flow)** | X |
| DB | JDBC/JPA | R2DBC | R2DBC/코루틴 | JDBC/JPA |
| 학습 곡선 | 낮음 | 높음 | 중간 | 낮음 |
| 디버깅 | 쉬움 | 어려움 | 비교적 쉬움 | 쉬움 |
| 언어 | Java/Kotlin | Java/Kotlin | **Kotlin** | Java/Kotlin |

---

## 10.6 언제 무엇을 쓰나 (의사결정)

### WebFlux(리액티브)를 쓰면 좋은 경우
- **동시 연결이 매우 많고**, 각 요청이 **다수의 느린 I/O**(외부 API 호출, MSA 팬아웃)를 함.
- **스트리밍 + 백프레셔**가 필요 (대용량 데이터, SSE/실시간 피드를 대규모로).
- 게이트웨이/프록시처럼 **I/O 중계가 주 업무**인 서비스 (Spring Cloud Gateway가 WebFlux 기반).
- 자원 효율(적은 스레드/메모리로 높은 처리량)이 중요한 고부하 서비스.

### 코루틴을 쓰면 좋은 경우
- **Kotlin** 팀이고, **논블로킹의 이점은 원하지만 Reactor의 복잡함은 피하고 싶을 때**.
- 비동기 흐름을 **읽기 쉬운 명령형 코드**로 유지하고 싶을 때.
- 여러 비동기 호출을 **구조적 동시성**으로 안전하게 조합해야 할 때.

### WebFlux/코루틴을 피하는 게 나은 경우 ⚠️
- 트래픽이 크지 않은 **일반적인 CRUD / 내부 어드민**.
- 파이프라인에 **블로킹 라이브러리(JDBC/JPA 등)** 가 많아 논블로킹을 끝까지 못 지킬 때.
- **CPU 바운드** 작업이 주력일 때(리액티브는 I/O 대기 해소가 본질이라 이점이 적음).
- 팀의 리액티브 숙련도가 낮아 **디버깅/유지보수 리스크**가 클 때.
- → 이 경우 대안: **Spring MVC (+ 필요하면 가상 스레드)**.

### 한 장 요약 트리

```
높은 I/O 동시성이 필요한가?
 ├─ 아니오 → Spring MVC (단순·검증됨)
 └─ 예 → 백프레셔/스트리밍이 필요한가?
          ├─ 예 → WebFlux(Java) 또는 코루틴 Flow(Kotlin)
          └─ 아니오 → 블로킹 코드를 끝까지 논블로킹으로 바꿀 수 있나?
                      ├─ 어렵다/팀 미숙 → MVC + 가상 스레드 (쉬운 고동시성)
                      └─ 가능/이미 리액티브 스택 → WebFlux 또는 코루틴
```

---

## 10.7 앞 장들과의 연결

- **WebClient**(02장)는 WebFlux 스택의 논블로킹 HTTP 클라이언트입니다. MVC에서도 쓸 수 있죠.
- **SSE**(05장)의 `Flux` 반환 예제가 바로 리액티브 스트리밍입니다.
- **WebSocket**(06장) 수만 동시 연결 처리에 논블로킹(Netty) 모델이 유리합니다.
- 즉, 이 장은 "앞에서 본 통신들을 **대규모로** 처리하는 서버의 엔진"에 관한 이야기입니다.

---

## 핵심 요약

- 병목은 보통 **I/O 대기**. Thread-per-request는 "노는 스레드"로 자원을 낭비한다.
- **WebFlux(Reactor)**: Netty 이벤트 루프 + `Mono`/`Flux` + **백프레셔**. 적은 스레드로 높은 동시성. 단, **끝까지 논블로킹**이어야 하고 코드가 어렵다.
- **코루틴(Kotlin)**: 논블로킹이지만 **동기 코드처럼 읽히는** `suspend` + 구조적 동시성. Reactor보다 직관적.
- **가상 스레드(Java 21+)**: **블로킹 코드 그대로** 높은 동시성 → "고동시성=WebFlux" 공식을 흔듦. 단, 백프레셔는 없음.
- 선택 기준: **단순/CRUD → MVC**, **고동시성 + 블로킹 라이브러리 → MVC+가상스레드**, **스트리밍/백프레셔/극한 효율 → WebFlux/코루틴**.

## 면접/실무 체크리스트

- [ ] Thread-per-request 모델의 한계는 무엇이며, 왜 I/O 바운드에서 두드러지나?
- [ ] WebFlux에서 블로킹 코드를 호출하면 왜 위험한가?
- [ ] 백프레셔란 무엇이고 왜 필요한가?
- [ ] 코루틴이 Reactor 대비 갖는 장점은? (가독성, 명령형, 구조적 동시성)
- [ ] 가상 스레드의 등장이 "WebFlux를 써야 하는 이유"를 어떻게 바꾸었나?

---

이전 → [09. 비동기 메시징](./09-messaging.md) | 다음 → [11. 기술 선택 가이드](./11-comparison-and-selection.md)
