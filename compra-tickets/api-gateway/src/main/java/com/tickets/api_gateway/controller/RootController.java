package com.tickets.api_gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "service", "api-gateway",
                "status", "ok",
                "message", "Sistema Distribuido de Venta de Tickets",
                "frontend", "http://10.66.1.52",
                "api", "http://10.66.1.51/api",
                "timestamp", Instant.now().toString()
        );
    }
}