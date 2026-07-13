package com.anushibinj.veemailer.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.anushibinj.veemailer.dto.OctaneFieldDto;
import com.anushibinj.veemailer.dto.OctaneFieldValueDto;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.metadata.FieldMetadata;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import com.hpe.adm.nga.sdk.query.Query;
import com.hpe.adm.nga.sdk.query.QueryMethod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bridges the Octane metadata APIs to the Easy Filter Builder UI.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>Return all <em>filterable</em> fields for a given entity type with their human-readable
 *       labels and type information so the UI can render the correct input control.</li>
 *   <li>Return the selectable values for a reference field (phases, users, releases, etc.)
 *       so the UI can show a searchable dropdown instead of raw Octane IDs.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OctaneMetadataService {
    private static final String ENTITY_TYPE_BACKLOG_ITEMS = "backlog_items";
    private static final List<String> BACKLOG_SUBTYPES = List.of("defect", "story", "quality_story");

    private final OctaneCacheService octaneCacheService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Returns all filterable, UI-visible fields for the given entity type.
     * Both the common {@code work_item} fields (name, phase, owner, …) and any
     * subtype-specific fields (severity for defect, story_points for story, …) are included.
     *
     * @param workspaceId ve-mailer workspace UUID
     * @param entityType  Octane entity subtype, e.g. "defect", "story", "feature"
     */
    public List<OctaneFieldDto> getFilterableFields(UUID workspaceId, String entityType) {
        Workspace workspace = loadWorkspace(workspaceId);
        Octane octane = buildOctaneClient(workspace);

        Collection<FieldMetadata> rawFields;
        try {
            if ("work_item".equals(entityType)) {
                rawFields = octane.metadata().fields("work_item").execute();
            } else if (ENTITY_TYPE_BACKLOG_ITEMS.equals(entityType)) {
                // Backlog Items combines defect, story, and quality_story fields.
                rawFields = octane.metadata().fields(
                        "work_item",
                        BACKLOG_SUBTYPES.get(0),
                        BACKLOG_SUBTYPES.get(1),
                        BACKLOG_SUBTYPES.get(2)
                ).execute();
            } else {
                // Include both common work_item fields and subtype-specific ones
                rawFields = octane.metadata().fields("work_item", entityType).execute();
            }
        } catch (Exception e) {
            workspaceService.markWorkspaceOffline(workspaceId,
                    "Workspace became unreachable while loading filter metadata: " + summarizeError(e));
            log.error("Failed to fetch field metadata for entity type '{}': {}", entityType, e.getMessage());
            throw new RuntimeException("Failed to fetch field metadata from Octane", e);
        }

        if (rawFields == null) {
            return List.of();
        }

        List<OctaneFieldDto> result = new ArrayList<>();
        // Track seen field names to deduplicate (work_item and subtype may share a field name)
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (FieldMetadata fm : rawFields) {
            if (!fm.isFilterable() || !fm.isVisibleInUI()) {
                continue;
            }
            if (!seen.add(fm.getName())) {
                continue; // duplicate — keep the first occurrence
            }
            result.add(toDto(fm));
        }
        result.sort(Comparator.comparing(OctaneFieldDto::getLabel, String.CASE_INSENSITIVE_ORDER));
        workspaceService.markWorkspaceOnline(workspaceId, !result.isEmpty());
        return result;
    }

    /**
     * Returns the selectable values for a reference field so the UI can show a
     * searchable dropdown populated with real names instead of raw Octane IDs.
     *
     * @param workspaceId ve-mailer workspace UUID
     * @param fieldName   Octane field name, e.g. "phase", "owner", "severity"
     * @param entityType  Octane entity subtype used to scope phases
     */
    public List<OctaneFieldValueDto> getFieldValues(UUID workspaceId, String fieldName, String entityType) {
        return getFieldValues(workspaceId, fieldName, entityType, null);
    }

    public List<OctaneFieldValueDto> getFieldValues(
            UUID workspaceId, String fieldName, String entityType, String searchQuery) {
        return getFieldValues(workspaceId, fieldName, entityType, searchQuery, null);
    }

    public List<OctaneFieldValueDto> getFieldValues(
            UUID workspaceId, String fieldName, String entityType, String searchQuery, String idsCsv) {
        Workspace workspace = loadWorkspace(workspaceId);
        Octane octane = buildOctaneClient(workspace);
        String normalizedSearch = normalizeSearch(searchQuery);
        List<String> requestedIds = normalizeIds(idsCsv);

        FieldMetadata fieldMeta = findFieldMetadata(octane, entityType, fieldName);
        if (fieldMeta == null || fieldMeta.getFieldType() != FieldMetadata.FieldType.Reference) {
            return List.of();
        }

        FieldMetadata.FieldTypeData typeData = fieldMeta.getFieldTypedata();
        if (typeData == null || typeData.getTargets() == null || typeData.getTargets().length == 0) {
            return List.of();
        }

        String targetType = typeData.getTargets()[0].getType();
        String logicalName = typeData.getTargets()[0].logicalName();

        try {
            List<OctaneFieldValueDto> values = fetchValuesForTarget(
                    octane, targetType, logicalName, entityType, normalizedSearch, requestedIds);
            workspaceService.markWorkspaceOnline(workspaceId, !values.isEmpty());
            return values;
        } catch (Exception e) {
            workspaceService.markWorkspaceOffline(workspaceId,
                    "Workspace became unreachable while loading field values: " + summarizeError(e));
            log.warn("Could not fetch values for field '{}' (target: {}): {}", fieldName, targetType, e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                    //
    // ------------------------------------------------------------------ //

    private List<OctaneFieldValueDto> fetchValuesForTarget(
            Octane octane,
            String targetType,
            String logicalName,
            String entityType,
            String searchQuery,
            List<String> requestedIds) {
        return switch (targetType) {
            case "list_node"      -> fetchListNodeValues(octane, logicalName, searchQuery, requestedIds);
            case "phase"          -> fetchPhaseValues(octane, entityType, searchQuery, requestedIds);
            case "workspace_user" -> fetchUserValues(octane, searchQuery, requestedIds);
            case "release"        -> fetchNamedEntityValues(octane, "releases", searchQuery, requestedIds);
            case "sprint"         -> fetchNamedEntityValues(octane, "sprints", searchQuery, requestedIds);
            case "product_area"   -> fetchNamedEntityValues(octane, "product_areas", searchQuery, requestedIds);
            case "team"           -> fetchNamedEntityValues(octane, "teams", searchQuery, requestedIds);
            case "milestone"      -> fetchNamedEntityValues(octane, "milestones", searchQuery, requestedIds);
            case "application_module" -> fetchNamedEntityValues(octane, "application_modules", searchQuery, requestedIds);
            default -> {
                log.debug("No value-fetch strategy for reference target type '{}' — returning empty list", targetType);
                yield List.of();
            }
        };
    }

    /** Fetches list nodes whose list_root has the given logical_name (severity, priority, …). */
    private List<OctaneFieldValueDto> fetchListNodeValues(
            Octane octane, String logicalName, String searchQuery, List<String> requestedIds) {
        Query.QueryBuilder filter = Query.statement(
                "list_root", QueryMethod.EqualTo, Query.statement("logical_name", QueryMethod.EqualTo, logicalName));
        if (!requestedIds.isEmpty()) {
            filter = filter.and(Query.statement("id", QueryMethod.In, toArray(requestedIds)));
        } else if (hasText(searchQuery)) {
            filter = filter.and(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)));
        }
        OctaneCollection<EntityModel> nodes = octane.entityList("list_nodes")
                .get()
                .addFields("id", "name", "logical_name")
                .query(filter.build())
                .execute();
        // Deduplicate by name — Octane may return inherited/archived entries with identical display names
        return toDeduplicatedValueDtos(nodes, "name");
    }

    /**
     * Fetches phases scoped to the given entity type (e.g. "defect", "story").
     * Without scoping, Octane returns phases for every entity type, producing many
     * duplicates — e.g. "Aborted" once per subtype that has an Aborted phase.
     * Falls back to name-deduplication when entity-type filtering is not supported.
     */
    private List<OctaneFieldValueDto> fetchPhaseValues(
            Octane octane, String entityType, String searchQuery, List<String> requestedIds) {
        if (ENTITY_TYPE_BACKLOG_ITEMS.equals(entityType)) {
            return fetchBacklogPhaseValues(octane, searchQuery, requestedIds);
        }
        if (!"work_item".equals(entityType)) {
            // Scope to the specific subtype so we don't show defect/story/feature phases mixed together
            try {
                Query.QueryBuilder scopedQuery = Query.statement("entity", QueryMethod.EqualTo, entityType);
                if (!requestedIds.isEmpty()) {
                    scopedQuery = scopedQuery.and(Query.statement("id", QueryMethod.In, toArray(requestedIds)));
                } else if (hasText(searchQuery)) {
                    scopedQuery = scopedQuery.and(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)));
                }
                OctaneCollection<EntityModel> phases = octane.entityList("phases")
                        .get()
                        .addFields("id", "name")
                        .query(scopedQuery.build())
                        .execute();
                if (phases != null && !phases.isEmpty()) {
                    return toSortedValueDtos(phases, "name");
                }
            } catch (Exception e) {
                log.warn("Could not filter phases by entity type '{}', falling back to deduplication: {}", entityType, e.getMessage());
            }
        }
        // Fallback: fetch all phases but deduplicate by name so the user sees each phase name once
        var getPhases = octane.entityList("phases")
                .get()
                .addFields("id", "name");
        if (!requestedIds.isEmpty()) {
            getPhases = getPhases.query(Query.statement("id", QueryMethod.In, toArray(requestedIds)).build());
        } else if (hasText(searchQuery)) {
            getPhases = getPhases.query(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)).build());
        }
        OctaneCollection<EntityModel> allPhases = getPhases.execute();
        return toDeduplicatedValueDtos(allPhases, "name");
    }

    private List<OctaneFieldValueDto> fetchBacklogPhaseValues(
            Octane octane, String searchQuery, List<String> requestedIds) {
        List<OctaneFieldValueDto> result = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.LinkedHashSet<>();
        for (String subtype : BACKLOG_SUBTYPES) {
            try {
                Query.QueryBuilder scopedQuery = Query.statement("entity", QueryMethod.EqualTo, subtype);
                if (!requestedIds.isEmpty()) {
                    scopedQuery = scopedQuery.and(Query.statement("id", QueryMethod.In, toArray(requestedIds)));
                } else if (hasText(searchQuery)) {
                    scopedQuery = scopedQuery.and(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)));
                }
                OctaneCollection<EntityModel> phases = octane.entityList("phases")
                        .get()
                        .addFields("id", "name")
                        .query(scopedQuery.build())
                        .execute();
                for (EntityModel entity : phases) {
                    String id = extractString(entity, "id");
                    String name = extractString(entity, "name");
                    if (id.isEmpty() || name.isEmpty() || !seenIds.add(id)) {
                        continue;
                    }
                    result.add(OctaneFieldValueDto.builder()
                            .id(id)
                            .name(name + " (" + subtype.replace('_', ' ') + ")")
                            .build());
                }
            } catch (Exception e) {
                log.warn("Could not fetch phases for backlog subtype '{}': {}", subtype, e.getMessage());
            }
        }
        if (result.isEmpty()) {
            var getPhases = octane.entityList("phases")
                    .get()
                    .addFields("id", "name");
            if (!requestedIds.isEmpty()) {
                getPhases = getPhases.query(Query.statement("id", QueryMethod.In, toArray(requestedIds)).build());
            } else if (hasText(searchQuery)) {
                getPhases = getPhases.query(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)).build());
            }
            OctaneCollection<EntityModel> allPhases = getPhases.execute();
            return toDeduplicatedValueDtos(allPhases, "name");
        }
        result.sort(Comparator.comparing(OctaneFieldValueDto::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Fetches workspace users and uses full_name (falling back to email) as the display value. */
    private List<OctaneFieldValueDto> fetchUserValues(Octane octane, String searchQuery, List<String> requestedIds) {
        var getUsers = octane.entityList("workspace_users")
                .get()
                .addFields("id", "full_name", "email", "name");
        if (!requestedIds.isEmpty()) {
            getUsers = getUsers.query(Query.statement("id", QueryMethod.In, toArray(requestedIds)).build());
        } else if (hasText(searchQuery)) {
            Query.QueryBuilder searchBuilder = Query.statement("full_name", QueryMethod.EqualTo, wildcard(searchQuery))
                    .or(Query.statement("email", QueryMethod.EqualTo, wildcard(searchQuery)))
                    .or(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)));
            if (isNumeric(searchQuery)) {
                searchBuilder = searchBuilder.or(Query.statement("id", QueryMethod.EqualTo, searchQuery.trim()));
            }
            getUsers = getUsers.query(searchBuilder.build());
        }
        OctaneCollection<EntityModel> users = getUsers.execute();

        List<OctaneFieldValueDto> result = new ArrayList<>();
        for (EntityModel entity : users) {
            String id = extractString(entity, "id");
            if (id.isEmpty()) continue;
            String fullName = extractString(entity, "full_name");
            String email    = extractString(entity, "email");
            String display  = !fullName.isEmpty() ? fullName : (!email.isEmpty() ? email : id);
            result.add(OctaneFieldValueDto.builder().id(id).name(display).build());
        }
        result.sort(Comparator.comparing(OctaneFieldValueDto::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Generic helper for entities that have `id` and `name` fields (releases, teams, …). */
    private List<OctaneFieldValueDto> fetchNamedEntityValues(
            Octane octane, String entityListName, String searchQuery, List<String> requestedIds) {
        try {
            var getEntities = octane.entityList(entityListName)
                    .get()
                    .addFields("id", "name");
            if (!requestedIds.isEmpty()) {
                getEntities = getEntities.query(Query.statement("id", QueryMethod.In, toArray(requestedIds)).build());
            } else if (hasText(searchQuery)) {
                getEntities = getEntities.query(Query.statement("name", QueryMethod.EqualTo, wildcard(searchQuery)).build());
            }
            OctaneCollection<EntityModel> entities = getEntities.execute();
            return toSortedValueDtos(entities, "name");
        } catch (Exception e) {
            log.warn("Could not fetch values from entity list '{}': {}", entityListName, e.getMessage());
            return List.of();
        }
    }

    private List<OctaneFieldValueDto> toSortedValueDtos(OctaneCollection<EntityModel> entities, String nameField) {
        List<OctaneFieldValueDto> result = new ArrayList<>();
        for (EntityModel entity : entities) {
            String id   = extractString(entity, "id");
            String name = extractString(entity, nameField);
            if (id.isEmpty() || name.isEmpty()) continue;
            result.add(OctaneFieldValueDto.builder().id(id).name(name).build());
        }
        result.sort(Comparator.comparing(OctaneFieldValueDto::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Same as {@link #toSortedValueDtos} but deduplicates by display name, keeping the first
     * occurrence of each name.  Used as a fallback when entity-type scoping isn't available
     * (e.g. phases fallback: "Aborted" appears once even if multiple subtypes define it).
     */
    private List<OctaneFieldValueDto> toDeduplicatedValueDtos(OctaneCollection<EntityModel> entities, String nameField) {
        java.util.Map<String, OctaneFieldValueDto> seen = new java.util.LinkedHashMap<>();
        for (EntityModel entity : entities) {
            String id   = extractString(entity, "id");
            String name = extractString(entity, nameField);
            if (id.isEmpty() || name.isEmpty()) continue;
            // putIfAbsent keeps the first occurrence of each unique name
            seen.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT),
                    OctaneFieldValueDto.builder().id(id).name(name).build());
        }
        List<OctaneFieldValueDto> result = new ArrayList<>(seen.values());
        result.sort(Comparator.comparing(OctaneFieldValueDto::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Extracts a plain string value from a field of an EntityModel.
     * Handles StringFieldModel and also attempts to read the raw value for other types.
     */
    private String extractString(EntityModel entity, String fieldName) {
        FieldModel<?> fm = entity.getValue(fieldName);
        if (fm == null || !fm.hasValue() || fm.getValue() == null) return "";
        if (fm instanceof StringFieldModel sfm) {
            return sfm.getValue() != null ? sfm.getValue() : "";
        }
        if (fm instanceof ReferenceFieldModel rfm) {
            EntityModel ref = rfm.getValue();
            if (ref == null) return "";
            FieldModel<?> nameFm = ref.getValue("name");
            if (nameFm instanceof StringFieldModel) return ((StringFieldModel) nameFm).getValue();
            return "";
        }
        return fm.getValue().toString();
    }

    /**
     * Fetches field metadata for the given entity type and looks up the field by name.
     * Searches both work_item (common) and subtype-specific fields.
     */
    private FieldMetadata findFieldMetadata(Octane octane, String entityType, String fieldName) {
        Collection<FieldMetadata> fields;
        try {
            if ("work_item".equals(entityType)) {
                fields = octane.metadata().fields("work_item").execute();
            } else if (ENTITY_TYPE_BACKLOG_ITEMS.equals(entityType)) {
                fields = octane.metadata().fields(
                        "work_item",
                        BACKLOG_SUBTYPES.get(0),
                        BACKLOG_SUBTYPES.get(1),
                        BACKLOG_SUBTYPES.get(2)
                ).execute();
            } else {
                fields = octane.metadata().fields("work_item", entityType).execute();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch metadata to look up field '{}': {}", fieldName, e.getMessage());
            return null;
        }
        if (fields == null) return null;
        return fields.stream().filter(f -> fieldName.equals(f.getName())).findFirst().orElse(null);
    }

    /** Maps an Octane {@link FieldMetadata} to the slim DTO returned to the frontend. */
    private OctaneFieldDto toDto(FieldMetadata fm) {
        boolean isRef = fm.getFieldType() == FieldMetadata.FieldType.Reference;
        String targetEntityType = null;
        String targetLogicalName = null;
        boolean isMultiRef = false;

        if (isRef && fm.getFieldTypedata() != null) {
            FieldMetadata.FieldTypeData td = fm.getFieldTypedata();
            isMultiRef = td.isMultiple();
            if (td.getTargets() != null && td.getTargets().length > 0) {
                targetEntityType = td.getTargets()[0].getType();
                targetLogicalName = td.getTargets()[0].logicalName();
            }
        }

        String label = (fm.getLabel() != null && !fm.getLabel().isBlank())
                ? fm.getLabel()
                : fm.getName();
        String fieldType = (fm.getFieldType() != null)
                ? fm.getFieldType().name().toLowerCase()
                : "string";

        return OctaneFieldDto.builder()
                .name(fm.getName())
                .label(label)
                .fieldType(fieldType)
                .reference(isRef)
                .multiReference(isMultiRef)
                .targetEntityType(targetEntityType)
                .targetLogicalName(targetLogicalName)
                .build();
    }

    private Workspace loadWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
    }

    private Octane buildOctaneClient(Workspace workspace) {
        return octaneCacheService.getOctaneClient(
                workspace.getRootUrl(),
                workspace.getClientId(),
                workspace.getClientKey(),
                Integer.parseInt(workspace.getSharedSpaceId()),
                Integer.parseInt(workspace.getWorkspaceId()));
    }

    private String summarizeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String normalizeSearch(String searchQuery) {
        if (searchQuery == null) {
            return null;
        }
        String trimmed = searchQuery.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> normalizeIds(String idsCsv) {
        if (!hasText(idsCsv)) {
            return List.of();
        }
        return java.util.Arrays.stream(idsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private String[] toArray(List<String> values) {
        return values.toArray(new String[0]);
    }

    private boolean isNumeric(String value) {
        if (!hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String wildcard(String searchQuery) {
        String trimmed = searchQuery.trim();
        if ("*".equals(trimmed)) {
            return "*";
        }
        return "*" + trimmed + "*";
    }
}
