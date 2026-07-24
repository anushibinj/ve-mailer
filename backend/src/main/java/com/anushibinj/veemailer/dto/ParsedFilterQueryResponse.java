package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.FilterCriteriaClause;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedFilterQueryResponse {
    private List<String> fields;
    private List<FilterCriteriaClause> criteria;
    private String orderBy;
    private String orderByDirection;
    private String filterQueryString;
}
