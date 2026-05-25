package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.GeneralSettingsDto;
import com.anushibinj.veemailer.model.GeneralSettings;
import com.anushibinj.veemailer.repository.GeneralSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeneralSettingsService {

    private final GeneralSettingsRepository repository;

    /** Fallback value when no DB row has been saved yet. */
    @Value("${veemailer.query.limit:25}")
    private int defaultQueryLimit;

    /**
     * Returns the current query limit.
     * Reads from the database if a row exists; falls back to
     * {@code veemailer.query.limit} from {@code application.properties}.
     */
    public int getQueryLimit() {
        List<GeneralSettings> all = repository.findAll();
        return all.isEmpty() ? defaultQueryLimit : all.get(0).getQueryLimit();
    }

    /** Returns the current settings as a DTO for the admin UI. */
    public GeneralSettingsDto get() {
        return GeneralSettingsDto.builder()
                .queryLimit(getQueryLimit())
                .build();
    }

    /**
     * Creates or replaces the single general-settings row.
     *
     * @param dto must have {@code queryLimit} either {@code -1} (unlimited) or a positive integer.
     * @throws IllegalArgumentException if the value is 0 or less than -1.
     */
    public GeneralSettingsDto update(GeneralSettingsDto dto) {
        int limit = dto.getQueryLimit();
        if (limit != -1 && limit <= 0) {
            throw new IllegalArgumentException(
                    "queryLimit must be a positive integer or -1 (unlimited). Got: " + limit);
        }

        List<GeneralSettings> all = repository.findAll();
        GeneralSettings entity = all.isEmpty()
                ? GeneralSettings.builder().queryLimit(limit).build()
                : all.get(0);
        entity.setQueryLimit(limit);
        repository.save(entity);

        return GeneralSettingsDto.builder().queryLimit(limit).build();
    }
}
