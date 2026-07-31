package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.AppUser;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query(value = "DELETE FROM user_roles WHERE user_id = :userId", nativeQuery = true)
    void deleteUserRoleMappings(@Param("userId") UUID userId);

    /**
     * Returns all users holding the given role (e.g. "ADMIN" — this app's Super Admin role).
     * Used to build recipient lists for admin-only notification emails.
     */
    @Query("SELECT DISTINCT u FROM AppUser u JOIN u.roles r WHERE r.roleName = :roleName")
    List<AppUser> findByRoleName(@Param("roleName") String roleName);
}
