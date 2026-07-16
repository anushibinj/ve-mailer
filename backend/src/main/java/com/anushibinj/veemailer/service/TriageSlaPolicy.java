package com.anushibinj.veemailer.service;

import com.hpe.adm.nga.sdk.model.DateFieldModel;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.anushibinj.veemailer.model.TriageSlaThreshold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Central policy for "Triage SLA" display and ordering.
 * Edit {@link #SLA_BANDS} to adjust color ranges.
 */
public final class TriageSlaPolicy {
    public static final String TRIAGE_SLA_FIELD = "Triage SLA";
    public static final String CREATION_TIME_FIELD = "creation_time";
    private static final String UNKNOWN_LABEL = "Unknown age";
    private static final Clock UTC_CLOCK = Clock.systemUTC();

    private static final List<SlaBand> SLA_BANDS = List.of(
            // 7+ days: red
            new SlaBand(7, Integer.MAX_VALUE, "\uD83D\uDD34"),
            // 3-4 days: yellow
            new SlaBand(3, 4, "\uD83D\uDFE1"),
            // 0-2 days: green
            new SlaBand(0, 2, "\uD83D\uDFE2")
    );

    private TriageSlaPolicy() {
    }

    public static String toDisplayLabel(EntityModel entity) {
        java.util.OptionalInt daysOld = daysSinceCreation(entity);
        return daysOld.isPresent() ? toDisplayLabel(daysOld.getAsInt()) : UNKNOWN_LABEL;
    }

    static String toDisplayLabel(int daysOld) {
        String emoji = SLA_BANDS.stream()
                .filter(band -> band.includes(daysOld))
                .map(SlaBand::emoji)
                .findFirst()
                .orElse("\uD83D\uDFE2");
        String dayLabel = daysOld == 1 ? "day" : "days";
        return emoji + " " + daysOld + " " + dayLabel + " old";
    }

    public static int daysSinceCreationOrDefault(EntityModel entity, int fallback) {
        return daysSinceCreation(entity).orElse(fallback);
    }

    public static boolean meetsThreshold(EntityModel entity, TriageSlaThreshold threshold) {
        TriageSlaThreshold effective = threshold == null ? TriageSlaThreshold.GREEN : threshold;
        if (effective == TriageSlaThreshold.GREEN) {
            return true;
        }
        java.util.OptionalInt daysOld = daysSinceCreation(entity);
        if (daysOld.isEmpty()) {
            return false;
        }
        int days = daysOld.getAsInt();
        return switch (effective) {
            case YELLOW -> days >= 3;
            case RED -> days >= 7;
            default -> true;
        };
    }

    private static java.util.OptionalInt daysSinceCreation(EntityModel entity) {
        if (entity == null) {
            return java.util.OptionalInt.empty();
        }
        FieldModel<?> creationTimeField = entity.getValue(CREATION_TIME_FIELD);
        Instant createdAt = extractInstant(creationTimeField);
        if (createdAt == null) {
            return java.util.OptionalInt.empty();
        }
        long days = Duration.between(createdAt, Instant.now(UTC_CLOCK)).toDays();
        return java.util.OptionalInt.of((int) Math.max(0, days));
    }

    private static Instant extractInstant(FieldModel<?> creationTimeField) {
        if (creationTimeField == null || !creationTimeField.hasValue() || creationTimeField.getValue() == null) {
            return null;
        }
        Object raw = creationTimeField.getValue();
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (raw instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (creationTimeField instanceof DateFieldModel dfm && dfm.getValue() != null) {
            return dfm.getValue().toInstant();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s);
            } catch (DateTimeParseException ignored) {
                try {
                    return OffsetDateTime.parse(s).toInstant();
                } catch (DateTimeParseException ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }

    private record SlaBand(int minDaysInclusive, int maxDaysInclusive, String emoji) {
        boolean includes(int daysOld) {
            return daysOld >= minDaysInclusive && daysOld <= maxDaysInclusive;
        }
    }
}
