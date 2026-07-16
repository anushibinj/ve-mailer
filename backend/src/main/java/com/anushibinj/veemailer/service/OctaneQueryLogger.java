package com.anushibinj.veemailer.service;

import com.hpe.adm.nga.sdk.query.Query;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

final class OctaneQueryLogger {

    private OctaneQueryLogger() {
    }

    static void log(Logger log, String endpoint, Query query, Collection<String> fields) {
        log.info(
                "Octane query: endpoint={}, query={}, fields={}",
                normalize(endpoint),
                query == null ? "-" : normalize(query.toString()),
                toFieldsString(fields)
        );
    }

    static void log(Logger log, String endpoint, Query query, String... fields) {
        log(log, endpoint, query, Arrays.asList(fields));
    }

    static void log(Logger log, String endpoint, String query, Collection<String> fields) {
        log.info(
                "Octane query: endpoint={}, query={}, fields={}",
                normalize(endpoint),
                normalize(query),
                toFieldsString(fields)
        );
    }

    private static String toFieldsString(Collection<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "-";
        }
        String joined = fields.stream()
                .map(OctaneQueryLogger::normalize)
                .collect(Collectors.joining(","));
        return joined.isBlank() ? "-" : joined;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }
}
