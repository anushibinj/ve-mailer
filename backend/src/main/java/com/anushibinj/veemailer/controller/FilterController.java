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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/filters")
@RequiredArgsConstructor
public class FilterController {

    private final FilterRepository filterRepository;
    private final FilterService filterService;

    @GetMapping
    public ResponseEntity<List<Filter>> getFilters() {
        return ResponseEntity.ok(filterRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Filter> createFilter(@Valid @RequestBody FilterDto dto) {
        Filter saved = filterService.createFilter(dto);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{filterId}/execute")
    public ResponseEntity<List<EntityModel>> executeFilter(
            @PathVariable UUID filterId,
            @RequestParam UUID workspaceId) {
        List<EntityModel> results = filterService.executeFilter(filterId, workspaceId);
        return ResponseEntity.ok(results);
    }
}
