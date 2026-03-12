package com.anushibinj.veemailer.service.extractor;

import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;

/**
 * Base extractor for Octane reference fields (single-value {@link ReferenceFieldModel}).
 *
 * <p>Tries each of the supplied {@code subFields} in order and returns the
 * value of the first non-blank one found on the nested entity.  Falls back to
 * the entity's {@code id} if none match, and to an empty string if the field
 * is absent or null.
 *
 * <p>Subclasses declare which sub-fields they prefer:
 * <pre>
 *   // "name" first, then id
 *   new ReferenceExtractor("name")
 *
 *   // "full_name" first, then "name", then id
 *   new ReferenceExtractor("full_name", "name")
 * </pre>
 */
public class ReferenceExtractor implements FieldValueExtractor {

    private final String[] subFields;

    public ReferenceExtractor(String... subFields) {
        this.subFields = subFields;
    }

    @Override
    public String extract(FieldModel<?> fieldModel) {
        if (!(fieldModel instanceof ReferenceFieldModel rfm)) return "";
        EntityModel ref = rfm.getValue();
        if (ref == null) return "";

        for (String sub : subFields) {
            FieldModel<?> candidate = ref.getValue(sub);
            if (candidate instanceof StringFieldModel s) {
                String v = s.getValue();
                if (v != null && !v.isBlank()) return v;
            }
        }

        String id = ref.getId();
        return id != null ? id : "";
    }
}
