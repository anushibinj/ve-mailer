package com.anushibinj.veemailer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Stores application-wide general settings in the database.
 * Only one row is expected; the admin UI creates it on first save.
 * When no row exists, the application falls back to {@code veemailer.query.limit}
 * from {@code application.properties}.
 */
@Entity
@Table(name = "general_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Maximum number of tickets to include in each mail report.
     * {@code -1} means unlimited.
     */
    @Column(nullable = false)
    private int queryLimit;
}
