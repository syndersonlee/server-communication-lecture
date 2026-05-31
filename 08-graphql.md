# 08. GraphQL

> "필요한 데이터만, 한 번의 요청으로." REST의 over/under-fetching 문제를 풀기 위해
> 페이스북이 만든 쿼리 언어이자 런타임입니다.

---

## 8.1 GraphQL이란

**GraphQL**: 클라이언트가 **필요한 데이터의 형태를 직접 명시**해서 요청하는 API 쿼리 언어.
보통 **단일 엔드포인트(`/graphql`)** 에 **HTTP POST**로 쿼리를 보냅니다.

- 프로토콜이 아니라 **HTTP 위에서 동작하는 쿼리 언어 + 런타임**입니다.
- (실시간 구독은 보통 WebSocket을 함께 사용 → `subscription`)

---

## 8.2 REST의 두 가지 고질병

### Over-fetching (과다 조회)
- `/users/1`이 유저의 모든 필드(이름, 주소, 결제내역...)를 반환하는데, 나는 이름만 필요.
- → 불필요한 데이터까지 받음 (특히 모바일에서 낭비).

### Under-fetching (과소 조회) → N+1 요청
- 화면 하나 그리려면 `/users/1` → `/users/1/reservations` → 각 예약의 `/hotels/{id}` ...
- → 요청을 여러 번 해야 함 (라운드트립 증가).

```
[REST] 화면 1개 = 요청 N개
GET /users/1
GET /users/1/reservations
GET /hotels/55
GET /hotels/77   ...

[GraphQL] 화면 1개 = 요청 1개 (필요한 것만 정확히)
POST /graphql  { user(id:1){ name reservations{ hotel{ name } } } }
```

---

## 8.3 핵심 개념

GraphQL은 3가지 작업(operation) 타입을 가집니다.

- **Query**: 조회 (REST의 GET).
- **Mutation**: 변경 (POST/PUT/DELETE).
- **Subscription**: 실시간 구독 (주로 WebSocket).

### 스키마 정의 (SDL)

```graphql
type Query {
  user(id: ID!): User
  hotels(city: String): [Hotel!]!
}

type Mutation {
  createReservation(input: ReservationInput!): Reservation!
}

type User {
  id: ID!
  name: String!
  reservations: [Reservation!]!
}

type Reservation {
  id: ID!
  checkIn: String!
  hotel: Hotel!
}

type Hotel {
  id: ID!
  name: String!
  city: String!
}
```

### 클라이언트 쿼리 → 응답

요청:
```graphql
query {
  user(id: 1) {
    name
    reservations {
      checkIn
      hotel { name }
    }
  }
}
```

응답 (요청한 형태와 **정확히 동일한 구조**):
```json
{
  "data": {
    "user": {
      "name": "홍길동",
      "reservations": [
        { "checkIn": "2026-06-01", "hotel": { "name": "야놀자호텔 강남" } }
      ]
    }
  }
}
```

---

## 8.4 Spring for GraphQL

`spring-boot-starter-graphql` 의존성. 스키마(`src/main/resources/graphql/schema.graphqls`)를 두고
리졸버를 `@Controller`로 작성합니다.

```java
@Controller
public class UserGraphQlController {

    private final UserService userService;
    private final ReservationService reservationService;

    public UserGraphQlController(UserService u, ReservationService r) {
        this.userService = u; this.reservationService = r;
    }

    @QueryMapping
    public User user(@Argument Long id) {
        return userService.findById(id);
    }

    // User.reservations 필드를 그릴 때 호출되는 리졸버
    @SchemaMapping(typeName = "User", field = "reservations")
    public List<Reservation> reservations(User user) {
        return reservationService.findByUserId(user.getId());
    }

    @MutationMapping
    public Reservation createReservation(@Argument ReservationInput input) {
        return reservationService.create(input);
    }
}
```

> ⚠️ **N+1 문제 주의**: `User.reservations`처럼 필드별 리졸버가 있으면
> 유저 10명 조회 시 예약 쿼리가 10번 나갈 수 있습니다.
> → **DataLoader(배치 로딩)** 로 묶어서 해결하는 게 GraphQL 운영의 핵심 과제.

---

## 8.5 장단점

### 장점
- 클라이언트가 **필요한 필드만** 조회 → over/under-fetching 해결, 모바일 트래픽 절약.
- **단일 엔드포인트**, 강타입 스키마(자동 문서화, 타입 안정성).
- 프론트엔드가 백엔드 배포 없이 필요한 데이터 조합 가능 → **프론트 개발 속도↑**.

### 단점 ⚠️
- **캐싱이 어렵다**: REST는 URL+HTTP 캐시가 쉬운데, GraphQL은 POST 단일 엔드포인트라 HTTP 캐시 활용 곤란.
- **N+1 문제**: DataLoader 없이는 성능 함정.
- **복잡도/보안**: 악의적으로 깊은 중첩 쿼리 → 서버 부하 (쿼리 깊이/복잡도 제한 필요).
- 단순 CRUD엔 오버킬일 수 있음.

---

## 8.6 언제 GraphQL?

| 상황 | 추천 |
|------|------|
| 다양한 클라이언트(웹/iOS/안드)가 각기 다른 데이터 조합 필요 | **GraphQL** |
| 여러 백엔드 데이터를 한 번에 조합(BFF, 게이트웨이) | **GraphQL** |
| 단순/표준적인 CRUD, 강력한 HTTP 캐싱 필요 | REST |
| 서버 간 고성능 통신 | gRPC |

---

## 핵심 요약

- **GraphQL**: 클라이언트가 **필요한 데이터 형태를 직접 명시**하는 쿼리 언어. 단일 엔드포인트 POST.
- REST의 **over/under-fetching**을 해결. Query/Mutation/Subscription.
- Spring은 `spring-boot-starter-graphql` + `@QueryMapping`/`@SchemaMapping`.
- 대가로 **캐싱 곤란, N+1(→DataLoader), 쿼리 복잡도 제어**라는 운영 과제가 생긴다.
- 다양한 클라이언트/데이터 조합이 필요한 BFF에 강점.

## 면접/실무 체크리스트

- [ ] over-fetching과 under-fetching의 차이는? GraphQL이 어떻게 해결하나?
- [ ] GraphQL에서 캐싱이 REST보다 어려운 이유는?
- [ ] GraphQL의 N+1 문제와 DataLoader의 역할은?
- [ ] REST/gRPC/GraphQL을 한 줄로 구분한다면?

---

이전 → [07. gRPC](./07-grpc.md) | 다음 → [09. 비동기 메시징](./09-messaging.md)
