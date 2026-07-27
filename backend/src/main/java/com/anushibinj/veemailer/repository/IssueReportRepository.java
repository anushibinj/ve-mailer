package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.IssueReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IssueReportRepository extends JpaRepository<IssueReport, UUID> {
}
