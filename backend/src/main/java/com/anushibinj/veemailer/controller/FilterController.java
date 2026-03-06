package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.repository.FilterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/filters")
@RequiredArgsConstructor
public class FilterController {

    private final FilterRepository filterRepository;

    @GetMapping
    public ResponseEntity<List<Filter>> getFilters() {
        return ResponseEntity.ok(filterRepository.findAll());
    }
}
