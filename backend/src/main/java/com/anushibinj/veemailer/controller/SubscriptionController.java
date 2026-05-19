package com.anushibinj.veemailer.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Subscription CRUD endpoints have been moved to WorkspaceController
 * under /api/v1/workspaces/{workspaceId}/subscriptions.
 * The old OTP-based /request and /verify endpoints have been removed.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    // intentionally empty — subscription management is handled by WorkspaceController
}

