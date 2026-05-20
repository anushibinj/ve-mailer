package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.AiPreferencesUpdateDto;
import com.anushibinj.veemailer.service.AiPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-preferences")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AiPreferencesController {

    private final AiPreferencesService service;

    @GetMapping
    public ResponseEntity<AiPreferencesResponseDto> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<AiPreferencesResponseDto> update(
            @Valid @RequestBody AiPreferencesUpdateDto dto) {
        return ResponseEntity.ok(service.update(dto));
    }
}
