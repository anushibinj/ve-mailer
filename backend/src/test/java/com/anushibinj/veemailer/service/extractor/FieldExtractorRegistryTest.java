package com.anushibinj.veemailer.service.extractor;

import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FieldExtractorRegistryTest {

    private FieldExtractorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new FieldExtractorRegistry();
    }

    // ── registry lookups ──────────────────────────────────────────────────────

    @Test
    void testKnownField_ReturnsRegisteredExtractor() {
        FieldValueExtractor e = registry.forField("phase");
        assertNotNull(e);
        assertNotSame(FieldExtractorRegistry.DEFAULT, e,
                "phase should have an explicit extractor");
    }

    @Test
    void testUnknownField_ReturnsDefault() {
        FieldValueExtractor e = registry.forField("totally_unknown_field");
        assertSame(FieldExtractorRegistry.DEFAULT, e,
                "Unknown field should fall back to DEFAULT extractor");
    }

    @Test
    void testRegister_OverridesExisting() {
        ReferenceExtractor custom = new ReferenceExtractor("label");
        registry.register("phase", custom);
        assertSame(custom, registry.forField("phase"));
    }

    // ── ReferenceExtractor behaviour ──────────────────────────────────────────

    @Test
    void testReferenceExtractor_ReturnsFirstMatchingSubField() {
        EntityModel ref = new EntityModel(Set.of(
                new StringFieldModel("full_name", "Jane Doe"),
                new StringFieldModel("name", "jdoe")
        ));
        ReferenceExtractor extractor = new ReferenceExtractor("full_name", "name");
        String result = extractor.extract(new ReferenceFieldModel("owner", ref));
        assertEquals("Jane Doe", result, "Should prefer full_name over name");
    }

    @Test
    void testReferenceExtractor_FallsBackToNextSubField() {
        // full_name absent — should fall through to "name"
        EntityModel ref = new EntityModel(Set.of(
                new StringFieldModel("name", "New")
        ));
        ReferenceExtractor extractor = new ReferenceExtractor("full_name", "name");
        String result = extractor.extract(new ReferenceFieldModel("phase", ref));
        assertEquals("New", result, "Should fall back to name when full_name is absent");
    }

    @Test
    void testReferenceExtractor_FallsBackToId() {
        // Neither preferred sub-field present — falls back to entity id.
        // EntityModel.getId() reads the "id" StringFieldModel from the field set.
        EntityModel ref = new EntityModel(Set.of(
                new StringFieldModel("id", "phase.feature.new")
        ));
        ReferenceExtractor extractor = new ReferenceExtractor("full_name", "name");
        String result = extractor.extract(new ReferenceFieldModel("phase", ref));
        assertEquals("phase.feature.new", result, "Should fall back to entity id");
    }

    @Test
    void testReferenceExtractor_NullFieldModel_ReturnsEmpty() {
        ReferenceExtractor extractor = new ReferenceExtractor("name");
        assertEquals("", extractor.extract(null));
    }

    @Test
    void testReferenceExtractor_NonReferenceFieldModel_ReturnsEmpty() {
        ReferenceExtractor extractor = new ReferenceExtractor("name");
        // Pass a StringFieldModel — should return ""
        assertEquals("", extractor.extract(new StringFieldModel("name", "value")));
    }

    // ── known field spot-checks ───────────────────────────────────────────────

    @Test
    void testOwnerField_ExtractsFullName() {
        EntityModel ownerRef = new EntityModel(Set.of(
                new StringFieldModel("full_name", "Maggie Flavell")
        ));
        String result = registry.forField("owner")
                .extract(new ReferenceFieldModel("owner", ownerRef));
        assertEquals("Maggie Flavell", result);
    }

    @Test
    void testPhaseField_ExtractsName() {
        EntityModel phaseRef = new EntityModel(Set.of(
                new StringFieldModel("name", "In Progress")
        ));
        String result = registry.forField("phase")
                .extract(new ReferenceFieldModel("phase", phaseRef));
        assertEquals("In Progress", result);
    }

    @Test
    void testProductUdf_ExtractsName() {
        EntityModel productRef = new EntityModel(Set.of(
                new StringFieldModel("name", "RKYV CSP")
        ));
        String result = registry.forField("product_udf")
                .extract(new ReferenceFieldModel("product_udf", productRef));
        assertEquals("RKYV CSP", result);
    }
}
