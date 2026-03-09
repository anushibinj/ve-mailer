package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.service.FilterService;
import com.hpe.adm.nga.sdk.model.EntityModel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/filters")
@RequiredArgsConstructor
public class FilterController {

    private final FilterRepository filterRepository;
    private final FilterService filterService;

    @GetMapping
    public ResponseEntity<List<Filter>> getFilters(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(filterRepository.findByWorkspace_Id(workspaceId));
    }

    @PostMapping
    public ResponseEntity<Filter> createFilter(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody FilterDto dto) {
        // Ensure the workspaceId in the path is used (DTO may also carry it, path wins)
        dto.setWorkspaceId(workspaceId);
        Filter saved = filterService.createFilter(dto);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{filterId}")
    public ResponseEntity<Filter> updateFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId,
            @Valid @RequestBody FilterDto dto) {
        dto.setWorkspaceId(workspaceId);
        Filter updated = filterService.updateFilter(filterId, dto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{filterId}/execute")
    public ResponseEntity<List<EntityModel>> executeFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        List<EntityModel> results = filterService.executeFilter(filterId, workspaceId);
        return ResponseEntity.ok(results);
    }
}
