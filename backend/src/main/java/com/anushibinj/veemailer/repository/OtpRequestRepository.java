package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {

    Optional<OtpRequest> findByEmail(String email);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
