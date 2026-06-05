package com.tickets.queue.controller;

import com.tickets.queue.entity.SystemParameter;
import com.tickets.queue.service.SystemParameterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/params")
public class SystemParameterController {

    private final SystemParameterService systemParameterService;

    public SystemParameterController(SystemParameterService systemParameterService) {
        this.systemParameterService = systemParameterService;
    }

    /**
     * GET /api/params/{key}
     * Response: { "key": "MAX_CONCURRENT_BUYERS", "value": "10" }
     * 404 si la clave no existe.
     */
    @GetMapping("/{key}")
    public ResponseEntity<SystemParameter> getParameter(@PathVariable String key) {
        return ResponseEntity.ok(systemParameterService.getByKey(key));
    }
}