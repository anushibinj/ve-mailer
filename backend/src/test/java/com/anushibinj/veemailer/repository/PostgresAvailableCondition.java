package com.anushibinj.veemailer.repository;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.Socket;

/**
 * JUnit 5 condition that skips tests if PostgreSQL is not reachable on localhost:5432.
 * Use {@link EnabledIfPostgresAvailable} annotation on test classes.
 */
class PostgresAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try (Socket socket = new Socket("localhost", 5432)) {
            return ConditionEvaluationResult.enabled("PostgreSQL is reachable on localhost:5432");
        } catch (Exception e) {
            return ConditionEvaluationResult.disabled(
                    "PostgreSQL is not reachable on localhost:5432 — skipping integration test");
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @ExtendWith(PostgresAvailableCondition.class)
    @interface EnabledIfPostgresAvailable {
    }
}
