package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.InviteMagicLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteMagicLinkRepository extends JpaRepository<InviteMagicLink, UUID> {

    Optional<InviteMagicLink> findByEmail(String email);

    Optional<InviteMagicLink> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InviteMagicLink i where i.tokenHash = :tokenHash")
    Optional<InviteMagicLink> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
