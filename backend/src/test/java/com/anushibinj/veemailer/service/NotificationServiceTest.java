package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "from", "noreply@test.com");
    }

    /** Creates a real MimeMessage backed by an empty Session so MimeMessageHelper works. */
    private MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void testProcessAndSendNotifications_SendsOneEmailPerSubscriber() {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage(), newMimeMessage());

        EmailSubscriber sub1 = new EmailSubscriber();
        sub1.setRecipientEmail("user1@example.com");
        EmailSubscriber sub2 = new EmailSubscriber();
        sub2.setRecipientEmail("user2@example.com");

        notificationService.processAndSendNotifications(
                List.of(sub1, sub2), Collections.emptyList(), List.of("name"), 25);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void testProcessAndSendNotifications_EmptyList_NoEmailSent() {
        notificationService.processAndSendNotifications(
                Collections.emptyList(), Collections.emptyList(), List.of("name"), 25);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testProcessAndSendNotifications_SubjectLineCorrect() throws Exception {
        MimeMessage msg = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(msg);

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("check@example.com");

        notificationService.processAndSendNotifications(
                List.of(sub), Collections.emptyList(), List.of("name"), 25);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("[ve-emailer] Your Notification Digest", captor.getValue().getSubject());
    }

    // ── buildHtmlTable unit tests (package-private method, same package) ──────

    @Test
    void testBuildHtmlTable_EmptyResults_ShowsNoItemsMessage() {
        String html = notificationService.buildHtmlTable(Collections.emptyList(), List.of("name", "phase"), 25);
        assertTrue(html.contains("No items matched"), "Should show empty-state message");
        assertFalse(html.contains("<table"), "Should not render a table for empty results");
    }

    @Test
    void testBuildHtmlTable_FooterShowsLimit() {
        String html = notificationService.buildHtmlTable(Collections.emptyList(), List.of("name"), 10);
        assertTrue(html.contains("limited to 10 items"), "Footer should show configured limit");
    }

    @Test
    void testBuildHtmlTable_HeadersMatchFields() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("story_points", "5"),
                new StringFieldModel("phase", "In Progress")
        ));
        String htmlWithRows = notificationService.buildHtmlTable(List.of(entity), List.of("story_points", "phase"), 25);
        assertTrue(htmlWithRows.contains("Story Points"), "Header should humanise story_points");
        assertTrue(htmlWithRows.contains("Phase"),        "Header should humanise phase");
    }

    @Test
    void testBuildHtmlTable_CellValuesRendered() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "Fix login bug"),
                new StringFieldModel("phase", "Open")
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25);

        assertTrue(html.contains("Fix login bug"), "Cell should contain entity name");
        assertTrue(html.contains("Open"),          "Cell should contain entity phase");
    }

    @Test
    void testBuildHtmlTable_HtmlSpecialCharsEscaped() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "<script>alert('xss')</script>")
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name"), 25);

        assertFalse(html.contains("<script>"), "Raw <script> tag must not appear in output");
        assertTrue(html.contains("&lt;script&gt;"), "Tag must be HTML-escaped");
    }

    @Test
    void testBuildHtmlTable_MissingFieldRendersEmptyCell() {
        // entity has no "phase" field — should produce an empty <td>
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "Some item")
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25);

        assertTrue(html.contains("Some item"));
        // Two <td> elements expected; second one should be empty
        assertTrue(html.contains("<td style=\"padding:8px;\"></td>"),
                "Missing field should render as empty cell");
    }
}
