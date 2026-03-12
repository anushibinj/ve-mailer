package com.anushibinj.veemailer.service.extractor;

import com.hpe.adm.nga.sdk.model.FieldModel;

/**
 * Strategy for extracting a human-readable display value from a single Octane
 * {@link FieldModel}.
 *
 * <p>Implement this interface to handle fields whose nested structure differs
 * from the default (e.g. {@code owner} uses {@code full_name} instead of
 * {@code name}). Register the implementation in {@link FieldExtractorRegistry}.
 */
public interface FieldValueExtractor {

    /**
     * Extract a display string from the given field model.
     *
     * @param fieldModel the raw field model returned by the Octane SDK (may be {@code null})
     * @return a non-null display string; empty string if no value can be determined
     */
    String extract(FieldModel<?> fieldModel);
}
