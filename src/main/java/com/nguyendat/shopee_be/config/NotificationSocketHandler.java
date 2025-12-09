package com.nguyendat.shopee_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public NotificationSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper; 
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    String uri = session.getUri() != null ? session.getUri().toString() : "-";
    System.out.println("Notification client connected: " + session.getId() + " uri=" + uri);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        System.out.println("Notification client disconnected: " + session.getId());
    }

    // Phát notification cho tất cả client
    public void broadcastNotification(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            System.out.println("Broadcasting notification to sessions=" + sessions.size() + " payload=" + json);
            sessions.values().forEach(ws -> {
                try {
                    if (ws.isOpen()) {
                        ws.sendMessage(new TextMessage(json));
                    } else {
                        System.out.println("- skipped closed session: " + ws.getId());
                    }
                } catch (Exception e) {
                    System.err.println("Error sending to session " + ws.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
