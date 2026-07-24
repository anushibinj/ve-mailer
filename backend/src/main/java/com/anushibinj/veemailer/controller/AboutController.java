package com.anushibinj.veemailer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public endpoint that returns build-time metadata (date, time, version).
 * The data originates from META-INF/build-info.properties, which is written
 * into the JAR by the spring-boot-maven-plugin's build-info goal.
 * When running outside a full Maven build (e.g. IDE hot-reload) the properties
 * file is absent and each field falls back to "development".
 */
@RestController
@RequestMapping("/api/about")
public class AboutController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Optional<BuildProperties> buildProperties;

    public AboutController(@Autowired(required = false) BuildProperties buildProperties) {
        this.buildProperties = Optional.ofNullable(buildProperties);
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getAbout() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("name", "ve-mailer");

        buildProperties.ifPresentOrElse(bp -> {
            var buildInstant = bp.getTime();
            if (buildInstant != null) {
                var utcDateTime = buildInstant.atZone(ZoneOffset.UTC);
                info.put("buildDate", DATE_FMT.format(utcDateTime));
                info.put("buildTime", TIME_FMT.format(utcDateTime) + " UTC");
                info.put("buildTimestamp", buildInstant.toString());
            } else {
                info.put("buildDate", "unknown");
                info.put("buildTime", "unknown");
                info.put("buildTimestamp", "unknown");
            }
            info.put("version", bp.getVersion() != null ? bp.getVersion() : "unknown");
            info.put("artifact", bp.getArtifact() != null ? bp.getArtifact() : "unknown");
        }, () -> {
            // No build-info.properties found — running from the IDE without a Maven build
            info.put("buildDate", "development");
            info.put("buildTime", "development");
            info.put("buildTimestamp", "development");
            info.put("version", "development");
            info.put("artifact", "veemailer");
        });

        return ResponseEntity.ok(info);
    }
}
