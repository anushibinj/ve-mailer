package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response envelope returned by the filter preview endpoint.
 *
 * <p>Each entry in {@code records} is a flat {@code fieldName → displayValue}
 * map — reference fields are already resolved to human-readable strings
 * (e.g. {@code "phase" → "Rejected"}, {@code "owner" → "Jane Smith"}).
 * When AI Summary is requested, the summary text is stored under the
 * {@code "✨ AI Summary"} key inside each record.
 * When Triage SLA is requested, each record includes a computed
 * {@code "Triage SLA"} value (for example {@code "🟡 3 days old"}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewResponse {

    /** Flattened, display-ready records — one map per ticket. */
    private List<Map<String, String>> records;

    /** True when AI summaries were actually generated for this preview. */
    private boolean aiSummaryGenerated;
}
