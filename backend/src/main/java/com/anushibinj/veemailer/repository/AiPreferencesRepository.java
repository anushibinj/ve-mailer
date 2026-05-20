package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.AiPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiPreferencesRepository extends JpaRepository<AiPreferences, UUID> {
}
