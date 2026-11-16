package com.anushibinj.veemailer.service.job;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JUnit 5 condition that skips Testcontainers-based tests when no Docker daemon is reachable,
 * so {@code mvn verify -Ptestcontainers} fails clearly rather than hanging or erroring when run
 * somewhere without Docker (mirrors {@code PostgresAvailableCondition} in the repository package).
 */
class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available");
            }
        } catch (Exception ignored) {
            // fall through to disabled
        }
        return ConditionEvaluationResult.disabled("Docker is not available — skipping Testcontainers IT");
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(DockerAvailableCondition.class)
    @interface EnabledIfDockerAvailable {
    }
}
