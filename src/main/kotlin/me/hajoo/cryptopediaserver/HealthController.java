package me.hajoo.cryptopediaserver;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "cryptopedia-api");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cryptopedia")
    public ResponseEntity<Map<String, Object>> getCryptopedia() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Cryptopedia API!");
        response.put("version", "1.0.0");
        response.put("endpoints", new String[]{"/api/v1/health", "/api/v1/cryptopedia"});
        
        return ResponseEntity.ok(response);
    }
}