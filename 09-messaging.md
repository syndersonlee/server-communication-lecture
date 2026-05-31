# 09. 비동기 메시징 (Message Queue / Kafka / MQTT)

> 지금까지는 전부 "요청하고 응답을 기다리는" **동기** 통신이었습니다.
> 이번엔 패러다임을 바꿉니다. "메시지를 던져놓고 내 할 일 하기" — **비동기 메시징**입니다.

---

## 9.1 왜 비동기인가 — 동기 통신의 한계

주문 1건에 결제, 재고차감, 알림, 정산, 추천 갱신이 필요하다고 합시다.

```
[동기/직접 호출]
주문서버 ─▶ 결제서버 ─▶ 재고서버 ─▶ 알림서버 ─▶ 정산서버
  └ 하나라도 느리거나 죽으면 전체 실패. 응답 시간 = 모든 단계의 합.
  └ 서비스 간 강결합(tight coupling).
```

```
[비동기/메시지]
주문서버 ─▶ [메시지 브로커] ─▶ 결제서버
                          ├▶ 재고서버
                          ├▶ 알림서버
                          └▶ 정산서버
  └ 주문서버는 메시지만 던지고 즉시 응답. 나머지는 각자 알아서 처리.
  └ 알림서버가 잠깐 죽어도 메시지는 큐에 남아있다 나중에 처리(내결함성).
  └ 서비스 간 느슨한 결합(loose coupling).
```

비동기 메시징의 핵심 가치: **결합도↓, 확장성↑, 내결함성↑, 트래픽 완충(버퍼링)**.

---

## 9.2 두 가지 메시징 모델

### ① 메시지 큐 (Point-to-Point)
- 하나의 메시지를 **하나의 컨슈머**가 가져가 처리. (작업 분배)
- 여러 컨슈머가 있으면 나눠서 처리 → 부하 분산.

```
Producer ─▶ [ Queue ] ─▶ Consumer (한 명만 가져감)
```

### ② 발행-구독 (Publish-Subscribe)
- 하나의 메시지를 **구독한 모두**가 받음. (브로드캐스트)

```
                ┌─▶ Subscriber A
Publisher ─▶ [Topic] ─▶ Subscriber B
                └─▶ Subscriber C
```

---

## 9.3 주요 프로토콜/제품

| 이름 | 분류 | 한 줄 설명 |
|------|------|-----------|
| **AMQP** | 프로토콜 | 메시지 큐 표준 프로토콜. RabbitMQ가 대표 구현. |
| **RabbitMQ** | 브로커 | AMQP 기반. 유연한 라우팅, 전통적 메시지 큐. |
| **Kafka** | 스트리밍 플랫폼 | 대용량 이벤트 스트리밍, 로그 기반, 높은 처리량. |
| **MQTT** | 프로토콜 | 초경량 pub/sub. IoT/모바일에 최적. |
| **Redis Pub/Sub** | 인메모리 | 가볍고 빠르지만 메시지 영속성 없음. |

---

## 9.4 RabbitMQ (AMQP)

전통적인 **메시지 브로커**. **Exchange → Binding → Queue** 라우팅 모델이 유연합니다.

```
Producer ─▶ [Exchange] ──(라우팅 규칙)──▶ [Queue] ─▶ Consumer
```

- **Exchange 타입**: direct(키 일치), topic(패턴), fanout(전체 브로드캐스트), headers.
- 메시지 단위 처리, ack/재전송, 우선순위, TTL, DLQ(Dead Letter Queue) 등 풍부한 기능.
- **메시지를 소비하면 큐에서 사라짐** (Kafka와의 큰 차이).

### Spring (spring-boot-starter-amqp)

```java
@Component
public class OrderListener {
    @RabbitListener(queues = "order.created.queue")
    public void handle(OrderCreatedEvent event) {
        // 메시지 수신 시 자동 호출
        paymentService.process(event);
    }
}

// 발행
@Service
public class OrderService {
    private final RabbitTemplate rabbitTemplate;
    public OrderService(RabbitTemplate t) { this.rabbitTemplate = t; }

    public void createOrder(Order order) {
        // ... 주문 저장
        rabbitTemplate.convertAndSend("order.exchange", "order.created",
                new OrderCreatedEvent(order.getId()));
    }
}
```

> 💡 RabbitMQ는 "복잡한 라우팅 + 작업 분배 큐"가 필요할 때 강합니다.

---

## 9.5 Apache Kafka

단순 큐가 아니라 **분산 이벤트 스트리밍 플랫폼**. 대용량 로그/이벤트 처리에 최적.

핵심 개념:
- **Topic**: 메시지 카테고리. **Partition**으로 나뉘어 병렬 처리/확장.
- **Offset**: 각 메시지의 순번. 컨슈머가 "어디까지 읽었는지" 관리.
- **Consumer Group**: 같은 그룹 내에서 파티션을 나눠 읽음(분배), 그룹이 다르면 각자 전체를 읽음(pub/sub).
- ⭐ **메시지를 읽어도 사라지지 않음**: 디스크에 보존(retention). 여러 컨슈머가 각자 속도로 다시 읽기 가능.

```
Producer ─▶ Topic "orders"
              ├ Partition 0 ─┐
              ├ Partition 1  ├─▶ Consumer Group A (분배해서 읽음)
              └ Partition 2 ─┘
                            └─▶ Consumer Group B (독립적으로 또 읽음)
```

### RabbitMQ vs Kafka

| 항목 | RabbitMQ | Kafka |
|------|----------|-------|
| 모델 | 메시지 브로커(큐) | 이벤트 로그/스트림 |
| 소비 후 | 메시지 삭제 | 보존(재처리 가능) |
| 처리량 | 중간 | 매우 높음(초당 수십만+) |
| 순서 보장 | 큐 단위 | 파티션 단위 |
| 강점 | 복잡한 라우팅, 작업 큐 | 대용량 로그, 이벤트 소싱, 스트림 처리 |
| 사례 | 작업 분배, RPC-비동기화 | 로그 수집, 실시간 분석, MSA 이벤트 백본 |

### Spring (spring-kafka)

```java
@Component
public class OrderConsumer {
    @KafkaListener(topics = "orders", groupId = "inventory-service")
    public void consume(OrderCreatedEvent event) {
        inventoryService.decrease(event.getProductId(), event.getQty());
    }
}

@Service
public class OrderProducer {
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    public OrderProducer(KafkaTemplate<String, OrderCreatedEvent> t) { this.kafkaTemplate = t; }

    public void publish(OrderCreatedEvent event) {
        // key(주문ID)가 같으면 같은 파티션 → 순서 보장
        kafkaTemplate.send("orders", String.valueOf(event.orderId()), event);
    }
}
```

> 💡 Kafka는 "이벤트를 흘려보내고, 여러 소비자가 각자 목적대로 소비"하는
> **이벤트 기반 아키텍처(EDA)** 의 중추로 많이 쓰입니다.

---

## 9.6 MQTT — IoT/모바일을 위한 초경량 프로토콜

- **MQTT**: 매우 가벼운 pub/sub 프로토콜. 저전력·저대역폭 환경(IoT 센서, 모바일 푸시) 최적.
- **Broker** 중심의 topic 구독 (`home/livingroom/temperature`).
- **QoS 레벨**: 0(최대 한 번), 1(최소 한 번), 2(정확히 한 번) — 신뢰성 선택.
- 헤더가 작아 배터리/네트워크 비용 절감. (스마트홈, 차량, 웨어러블)

---

## 9.7 비동기 메시징의 어려운 점 ⚠️

비동기는 공짜가 아닙니다. 다음을 반드시 설계해야 합니다.

1. **메시지 중복**: 대부분 "최소 한 번(at-least-once)" 전달 → 중복 가능.
   - → **멱등 처리(idempotent consumer)** 필수. (처리한 메시지 ID 기록 등)
2. **순서 보장**: 전역 순서는 어려움. Kafka는 파티션 키로 부분 순서 보장.
3. **실패 처리**: 처리 실패 메시지를 **DLQ(Dead Letter Queue)** 로 보내 재처리/분석.
4. **트랜잭션 정합성**: DB 저장과 메시지 발행을 함께 보장하기 어려움.
   - → **Transactional Outbox 패턴**, Saga 패턴 등으로 해결.
5. **추적/관측**: 비동기는 흐름 추적이 어려움 → 분산 추적(트레이싱), 상관관계 ID 필요.

---

## 핵심 요약

- 비동기 메시징은 **결합도↓ · 확장성↑ · 내결함성↑ · 트래픽 완충**을 제공.
- 모델: **큐(P2P, 작업 분배)** vs **pub/sub(브로드캐스트)**.
- **RabbitMQ**: 유연한 라우팅의 전통 브로커, 소비하면 삭제.
- **Kafka**: 고처리량 이벤트 스트리밍, 메시지 보존·재처리, EDA의 중추.
- **MQTT**: IoT/모바일용 초경량 pub/sub.
- 대가로 **중복(멱등성), 순서, 실패(DLQ), 정합성(Outbox/Saga)** 설계가 필요.

## 면접/실무 체크리스트

- [ ] 동기 호출 대신 비동기 메시징을 쓰면 얻는 것은? (디커플링, 내결함성, 버퍼링)
- [ ] RabbitMQ와 Kafka의 가장 큰 차이는? (소비 후 삭제 vs 보존/재처리)
- [ ] "at-least-once" 전달에서 컨슈머가 반드시 갖춰야 할 성질은? (멱등성)
- [ ] DB 저장과 메시지 발행의 정합성을 맞추는 패턴은? (Transactional Outbox)

---

이전 → [08. GraphQL](./08-graphql.md) | 다음 → [10. WebFlux와 코루틴](./10-webflux-coroutine.md)
