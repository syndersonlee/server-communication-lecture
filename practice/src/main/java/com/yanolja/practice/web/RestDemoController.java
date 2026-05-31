package com.yanolja.practice.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [실습 1] HTTP / REST
 * 가장 기본이 되는 요청-응답 모델. 메서드/상태코드/멱등성을 직접 확인해본다.
 */
@RestController
@RequestMapping("/api/rest")
@CrossOrigin
public class RestDemoController {

    private final Map<Long, Reservation> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(9000);

    public RestDemoController() {
        // 데모용 초기 데이터
        long id = sequence.incrementAndGet();
        store.put(id, new Reservation(id, "야놀자호텔 강남", "2026-06-01", "CONFIRMED"));
    }

    /** GET: 단순 조회 (안전 · 멱등) */
    @GetMapping("/reservations")
    public List<Reservation> list() {
        return store.values().stream().toList();
    }

    @GetMapping("/reservations/{id}")
    public ResponseEntity<Reservation> get(@PathVariable Long id) {
        Reservation found = store.get(id);
        if (found == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        return ResponseEntity.ok(found); // 200
    }

    /** POST: 생성 (멱등하지 않음). 201 Created + Location 헤더 반환 */
    @PostMapping("/reservations")
    public ResponseEntity<Reservation> create(@Valid @RequestBody CreateRequest req) {
        long id = sequence.incrementAndGet();
        Reservation created = new Reservation(id, req.hotelName(), req.checkIn(), "CONFIRMED");
        store.put(id, created);
        return ResponseEntity
                .created(URI.create("/api/rest/reservations/" + id)) // 201 + Location
                .body(created);
    }

    /** DELETE: 삭제 (멱등). 여러 번 호출해도 결과는 동일 → 204 */
    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        store.remove(id);
        return ResponseEntity.noContent().build(); // 204
    }

    /** 상태코드 체험용: 원하는 상태코드를 그대로 돌려준다 */
    @GetMapping("/status/{code}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable int code) {
        Map<String, Object> body = Map.of(
                "requestedStatus", code,
                "meaning", meaning(code),
                "at", LocalDateTime.now().toString()
        );
        return ResponseEntity.status(HttpStatus.valueOf(code)).body(body);
    }

    private String meaning(int code) {
        return switch (code / 100) {
            case 2 -> "성공 (Success)";
            case 3 -> "리다이렉션 (Redirection)";
            case 4 -> "클라이언트 오류 (Client Error)";
            case 5 -> "서버 오류 (Server Error)";
            default -> "정보/기타";
        };
    }

    // --- DTO ---
    public record Reservation(Long id, String hotelName, String checkIn, String status) {}

    public record CreateRequest(
            @NotBlank(message = "hotelName은 필수입니다") String hotelName,
            @NotBlank(message = "checkIn은 필수입니다") String checkIn) {}
}
