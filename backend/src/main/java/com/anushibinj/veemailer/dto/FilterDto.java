package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.FilterCriteriaClause;
import jakarta.validation.constraints.NotBlank;
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

    private List<String> fields;

    private List<FilterCriteriaClause> criteria;

    /**
     * Optional compact form of filter definition:
     * fields=id,name&query=name EQ ^*Case360*^
     *
     * When provided, backend parsing/validation is used and generated fields/criteria
     * are persisted instead of the raw fields/criteria payload.
     */
    private String filterQueryString;
}
