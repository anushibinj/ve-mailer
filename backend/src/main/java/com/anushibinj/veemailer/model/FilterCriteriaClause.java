package com.anushibinj.veemailer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single clause in a filter's criteria.
 * Not a JPA entity — serialized as JSON inside Filter.criteria.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterCriteriaClause {

    /** Octane field name, e.g. "defect_type", "product_udf", "phase" */
    private String field;

    /** Operator: "IN" or "NOT_IN" */
    private String operator;

    /** One or more values (Octane entity IDs or literal strings) */
    private List<String> values;
}
