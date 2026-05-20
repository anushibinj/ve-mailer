package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.NotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, UUID> {
}
