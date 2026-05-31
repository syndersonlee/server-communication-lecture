package com.yanolja.practice.chat;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * [실습 4] WebSocket(STOMP) 채팅
 *
 * 클라이언트가 /app/chat.send 로 메시지를 보내면,
 * /topic/room 을 구독 중인 모든 클라이언트에게 브로드캐스트된다.
 */
@Controller
public class ChatController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @MessageMapping("/chat.send")
    @SendTo("/topic/room")
    public ChatMessage send(ChatMessage message) {
        return new ChatMessage(
                message.sender(),
                message.content(),
                LocalTime.now().format(TIME));
    }

    public record ChatMessage(String sender, String content, String time) {}
}
