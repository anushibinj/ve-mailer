package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.NotificationPreferencesResponseDto;
import com.anushibinj.veemailer.dto.NotificationPreferencesUpdateDto;
import com.anushibinj.veemailer.service.NotificationPreferencesService;
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
@RequestMapping("/api/admin/notification-preferences")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class NotificationPreferencesController {

    private final NotificationPreferencesService service;

    @GetMapping
    public ResponseEntity<NotificationPreferencesResponseDto> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping
    public ResponseEntity<NotificationPreferencesResponseDto> update(
            @Valid @RequestBody NotificationPreferencesUpdateDto dto) {
        return ResponseEntity.ok(service.update(dto));
    }
}
