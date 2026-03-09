package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.FilterCriteriaClause;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterDto {

    // Not validated here — always injected from the path variable in the controller
    private UUID workspaceId;

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String entityType;

    @NotEmpty
    private List<String> fields;

    @NotEmpty
    private List<FilterCriteriaClause> criteria;
}
