package com.anushibinj.veemailer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceConnectivityPollingService {

    private final WorkspaceService workspaceService;

    /** Runs workspace connectivity checks at the top of every hour. */
    @Scheduled(cron = "0 0 * * * *")
    public void refreshWorkspaceConnectivityHourly() {
        log.info("Running hourly workspace connectivity refresh");
        workspaceService.refreshConnectivityForAllWorkspaces();
    }
}

