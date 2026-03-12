package com.anushibinj.veemailer.service.extractor;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that maps Octane field names to their {@link FieldValueExtractor}.
 *
 * <p>Field names that are not explicitly registered fall back to the
 * {@link #DEFAULT} extractor, which tries {@code name} then {@code id}.
 *
 * <h3>Adding support for a new field</h3>
 * Call {@code register()} from the constructor with the Octane field name and
 * the appropriate extractor.  For reference fields use {@link ReferenceExtractor}
 * and pass the sub-field names in preference order:
 *
 * <pre>
 *   register("team_lead", new ReferenceExtractor("full_name", "name"));
 * </pre>
 */
@Component
public class FieldExtractorRegistry {

    /**
     * Fallback extractor: tries {@code name} first (most common), then {@code id}.
     * Works for phase, product_udf, and any generic list_node / entity reference.
     */
    public static final ReferenceExtractor DEFAULT = new ReferenceExtractor("name");

    private final Map<String, FieldValueExtractor> registry = new HashMap<>();

    public FieldExtractorRegistry() {
        // ── reference fields whose preferred sub-field is NOT "name" ──────────

        // workspace_user: display name is in "full_name"
        register("owner",        new ReferenceExtractor("full_name", "name"));
        register("author",       new ReferenceExtractor("full_name", "name"));
        register("assigned_to",  new ReferenceExtractor("full_name", "name"));

        // ── reference fields that DO use "name" (explicit for documentation) ──
        // These are covered by DEFAULT, but listed here so it's obvious they
        // are known fields with no special handling needed.
        register("phase",        new ReferenceExtractor("name"));
        register("product_udf",  new ReferenceExtractor("name"));
        register("severity",     new ReferenceExtractor("name"));
        register("priority",     new ReferenceExtractor("name"));
        register("team",         new ReferenceExtractor("name"));
        register("sprint",       new ReferenceExtractor("name"));
        register("release",      new ReferenceExtractor("name"));
        register("milestone",    new ReferenceExtractor("name"));
        register("feature",      new ReferenceExtractor("name"));
        register("root",         new ReferenceExtractor("name"));
    }

    /**
     * Register a custom extractor for a specific Octane field name.
     * Can be called at runtime to extend the registry without recompilation.
     */
    public void register(String fieldName, FieldValueExtractor extractor) {
        registry.put(fieldName, extractor);
    }

    /**
     * Look up the extractor for a field name.
     *
     * @param fieldName the Octane field name (e.g. {@code "owner"}, {@code "phase"})
     * @return the registered extractor, or {@link #DEFAULT} if none is registered
     */
    public FieldValueExtractor forField(String fieldName) {
        return Optional.ofNullable(registry.get(fieldName)).orElse(DEFAULT);
    }
}
