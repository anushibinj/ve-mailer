package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.Filter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FilterRepository extends JpaRepository<Filter, UUID> {

    List<Filter> findByWorkspace_Id(UUID workspaceId);
}
