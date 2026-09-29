package com.jagapathi.pharmacy.inventory.api.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile({"local","test"})
@RequestMapping("/api/v1/debug")
public class DebugController {

    @PostMapping("/allocate-memory")
    public ResponseEntity<Map<String, String>> allocateMemory() {
        byte[] memory = new byte[1024 * 1024];
        memory[0] = 1;
        return ResponseEntity.ok(Map.of("status", "allocated"));
    }
}
