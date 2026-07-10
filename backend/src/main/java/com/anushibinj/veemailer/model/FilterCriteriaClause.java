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

    /**
     * How this clause is joined to the <em>previous</em> clause in the criteria list.
     * Accepted values: {@code "AND"} (default) or {@code "OR"}.
     * Ignored for the first clause in the list.
     * Null is treated as {@code "AND"} for backward compatibility with existing saved filters.
     */
    @Builder.Default
    private String logicalOperator = "AND";

    /**
     * Whether this clause's values represent reference IDs and therefore should be
     * serialized/built as {@code field EQ {id IN ...}}.
     *
     * <p>Null means "unspecified" (for backward compatibility with older saved filters).
     */
    private Boolean referenceValues;
}
