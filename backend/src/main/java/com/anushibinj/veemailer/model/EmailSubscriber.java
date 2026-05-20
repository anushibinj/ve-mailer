package com.anushibinj.veemailer.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "email_subscribers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "filter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_email_subscribers_filter_id"))
    private Filter filter;

    @ManyToOne
    @JoinColumn(name = "workspace_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_email_subscribers_workspace_id"))
    private Workspace workspace;

    private String recipientEmail;

    /** Legacy field – retained for backward-compatibility migration only. */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = true)
    private Frequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type")
    private ScheduleType scheduleType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscriber_scheduled_hours",
            joinColumns = @JoinColumn(name = "subscriber_id"),
            foreignKey = @ForeignKey(name = "fk_subscriber_hours_subscriber_id"))
    @Column(name = "scheduled_hour")
    private List<Integer> scheduledHours;

    @Enumerated(EnumType.STRING)
    private Status status;
}
