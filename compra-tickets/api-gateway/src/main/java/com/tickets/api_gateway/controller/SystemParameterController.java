package com.tickets.api_gateway.controller;


import com.tickets.api_gateway.client.PurchaseClient;
import com.tickets.api_gateway.dto.response.SystemParameterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/params")
@RequiredArgsConstructor
public class SystemParameterController {

    private final PurchaseClient purchaseClient;

    @GetMapping("/{key}")
    public ResponseEntity<SystemParameterResponse> getParameter(
            @PathVariable String key
    ) {
        log.debug("GET parameter key={}", key);

        SystemParameterResponse response =
                purchaseClient.getParameter(key);

        return ResponseEntity.ok(response);
    }
}