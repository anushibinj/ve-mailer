package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.GeneralSettingsDto;
import com.anushibinj.veemailer.service.GeneralSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/general-settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class GeneralSettingsController {

    private final GeneralSettingsService generalSettingsService;

    @GetMapping
    public ResponseEntity<GeneralSettingsDto> getSettings() {
        return ResponseEntity.ok(generalSettingsService.get());
    }

    @PutMapping
    public ResponseEntity<GeneralSettingsDto> updateSettings(@RequestBody GeneralSettingsDto dto) {
        return ResponseEntity.ok(generalSettingsService.update(dto));
    }
}
