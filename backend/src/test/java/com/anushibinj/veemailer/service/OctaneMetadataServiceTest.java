package com.anushibinj.veemailer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.adm.nga.sdk.model.EntityModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class OctaneMetadataServiceTest {

    @Mock
    private OctaneCacheService octaneCacheService;

    @Mock
    private com.anushibinj.veemailer.repository.WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceService workspaceService;

    private OctaneMetadataService service;

    @BeforeEach
    void setUp() {
        service = new OctaneMetadataService(
                octaneCacheService,
                workspaceRepository,
                workspaceService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "uiBundleFieldNamesCsv", "product_udf");
    }

    @SuppressWarnings("unchecked")
    @Test
    void extractEntityModels_ParsesUiBundleJsonArrayString() throws Exception {
        String rawValue = """
                [{"activity_level":0,"logical_name":"alpha_bundle_key","name":"Alpha Product","index":1,"id":"90001"},
                 {"activity_level":0,"logical_name":"beta_bundle_key","name":"Beta Product","index":2,"id":"90002"}]
                """;

        Method method = OctaneMetadataService.class.getDeclaredMethod("extractEntityModels", Object.class);
        method.setAccessible(true);

        List<EntityModel> result = (List<EntityModel>) method.invoke(service, rawValue);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        assertEquals("90001", result.get(0).getId());
        assertEquals("Alpha Product", result.get(0).getValue("name").getValue());
        assertEquals("90002", result.get(1).getId());
        assertEquals("Beta Product", result.get(1).getValue("name").getValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void extractEntityModels_ParsesIterableOfMaps() throws Exception {
        List<Map<String, Object>> rawValue = List.of(
                Map.of("id", "90011", "name", "Gamma Product", "logical_name", "gamma_bundle_key"),
                Map.of("id", "90012", "name", "Delta Product", "logical_name", "delta_bundle_key")
        );

        Method method = OctaneMetadataService.class.getDeclaredMethod("extractEntityModels", Object.class);
        method.setAccessible(true);

        List<EntityModel> result = (List<EntityModel>) method.invoke(service, rawValue);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        assertEquals("Gamma Product", result.get(0).getValue("name").getValue());
        assertEquals("Delta Product", result.get(1).getValue("name").getValue());
    }
}
