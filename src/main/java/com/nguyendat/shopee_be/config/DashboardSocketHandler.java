package com.nguyendat.shopee_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nguyendat.shopee_be.entities.DashboardEvent;
import com.nguyendat.shopee_be.services.DashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class DashboardSocketHandler extends TextWebSocketHandler {
    
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Autowired
    private DashboardService dashboardService; 


    public int getConnectedClients() {
        return sessions.size();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info(" Dashboard client connected: {} | Total: {}", 
                 session.getId(), sessions.size());
        
        // Gửi welcome message
        sendToSession(session, new DashboardEvent<>(
            "CONNECTION_SUCCESS", 
            "Connected to dashboard", 
            Instant.now().toString()
        ));
        
        // TỰ ĐỘNG GỬI CHARTS DATA SAU 500ms
        // CompletableFuture.runAsync(() -> {
        //     try {
        //         Thread.sleep(500); // Delay để client sẵn sàng nhận
                
        //         log.info(" Sending initial charts data to client {}", session.getId());
                
        //         dashboardService.pushKpiUpdate();
        //         dashboardService.pushHourlyRevenue();
        //         dashboardService.pushOrderStatusDistribution();
                
        //         log.info(" Initial charts data sent to client {}", session.getId());
        //     } catch (Exception e) {
        //         log.error(" Failed to send initial charts data: {}", e.getMessage());
        //     }
        // });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info(" Dashboard client disconnected: {} | Remaining: {}", 
                 session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error(" WebSocket error for session {}: {}", 
                  session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    //  Broadcast với error handling
    public void broadcast(DashboardEvent<?> event) {
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (IOException e) {
            log.error(" Failed to serialize event: {}", e.getMessage());
            return;
        }

        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                return true; // Remove closed sessions
            }
            
            try {
                session.sendMessage(new TextMessage(json));
                return false; // Keep session
            } catch (IOException e) {
                log.error(" Failed to send to session {}: {}", 
                          session.getId(), e.getMessage());
                return true; // Remove failed session
            }
        });
        
        log.debug(" Broadcasted {} to {} sessions", event.getType(), sessions.size());
    }

    //  Send to single session
    private void sendToSession(WebSocketSession session, DashboardEvent<?> event) {
        try {
            String json = mapper.writeValueAsString(event);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error(" Failed to send to session {}: {}", 
                      session.getId(), e.getMessage());
        }
    }

    // Helper method
    public void sendEvent(String type, Object payload) {
        DashboardEvent<Object> event = new DashboardEvent<>(
            type, payload, Instant.now().toString()
        );
        broadcast(event);
    }
}