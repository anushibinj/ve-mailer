package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.WorkspaceDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/workspaces")
    public ResponseEntity<Workspace> createWorkspace(@RequestBody WorkspaceDto dto) {
        Workspace created = adminService.createWorkspace(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/workspaces/{id}")
    public ResponseEntity<Workspace> updateWorkspace(@PathVariable UUID id, @RequestBody WorkspaceDto dto) {
        Workspace updated = adminService.updateWorkspace(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/workspaces/{id}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable UUID id) {
        adminService.deleteWorkspace(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/filters")
    public ResponseEntity<Filter> createFilter(@RequestBody Filter dto) {
        Filter created = adminService.createFilter(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/filters/{id}")
    public ResponseEntity<Filter> updateFilter(@PathVariable UUID id, @RequestBody Filter dto) {
        Filter updated = adminService.updateFilter(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/filters/{id}")
    public ResponseEntity<Void> deleteFilter(@PathVariable UUID id) {
        adminService.deleteFilter(id);
        return ResponseEntity.noContent().build();
    }
}
