package com.anushibinj.veemailer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DynamicMailSenderServiceTest {

    @Mock
    private NotificationPreferencesService notificationPreferencesService;

    @InjectMocks
    private DynamicMailSenderService dynamicMailSenderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dynamicMailSenderService, "adminEmail", "admin@company.com");
    }

    @Test
    void getFromAddress_ReturnsAdminEmail() {
        assertEquals("admin@company.com", dynamicMailSenderService.getFromAddress());
    }
}
