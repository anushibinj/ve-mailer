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

@Entity
@Table(name = "filters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String description;

    /** Octane subtype, e.g. "defect", "story", "feature" */
    private String entityType;

    /** JSON array of Octane field names to fetch, e.g. ["id","name","phase"] */
    @Column(columnDefinition = "TEXT")
    private String fields;

    /** JSON array of FilterCriteriaClause objects */
    @Column(columnDefinition = "TEXT")
    private String criteria;
}
