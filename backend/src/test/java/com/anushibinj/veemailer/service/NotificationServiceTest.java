package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.service.extractor.FieldExtractorRegistry;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    // Use a real registry so extractor behaviour is tested end-to-end.
    private final FieldExtractorRegistry registry = new FieldExtractorRegistry();

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(mailSender, registry);
        ReflectionTestUtils.setField(notificationService, "from", "noreply@test.com");
    }

    /** Creates a real MimeMessage backed by an empty Session so MimeMessageHelper works. */
    private MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    // ── processAndSendNotifications ───────────────────────────────────────────

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

    // ── buildHtmlTable ────────────────────────────────────────────────────────

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
        String html = notificationService.buildHtmlTable(List.of(entity), List.of("story_points", "phase"), 25);
        assertTrue(html.contains("Story Points"), "Header should humanise story_points");
        assertTrue(html.contains("Phase"),        "Header should humanise phase");
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
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "Some item")
        ));
        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25);
        assertTrue(html.contains("Some item"));
        assertTrue(html.contains("<td style=\"padding:8px;\"></td>"),
                "Missing field should render as empty cell");
    }

    // ── reference field extraction via registry ───────────────────────────────

    @Test
    void testBuildHtmlTable_PhaseReference_UsesName() {
        // phase is a reference whose display value is in "name"
        EntityModel phaseRef = new EntityModel(Set.of(
                new StringFieldModel("name", "New")
        ));
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature"),
                new ReferenceFieldModel("phase", phaseRef)
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25);

        assertTrue(html.contains("My Feature"), "name field should be rendered");
        assertTrue(html.contains("New"), "phase.name should be rendered as the cell value");
    }

    @Test
    void testBuildHtmlTable_OwnerReference_UsesFullName() {
        // owner is a workspace_user whose display value is in "full_name"
        EntityModel ownerRef = new EntityModel(Set.of(
                new StringFieldModel("full_name", "Maggie Flavell"),
                new StringFieldModel("name", "mflavell") // should NOT be picked
        ));
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature"),
                new ReferenceFieldModel("owner", ownerRef)
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "owner"), 25);

        assertTrue(html.contains("Maggie Flavell"), "owner.full_name should be the cell value");
        assertFalse(html.contains("mflavell"), "owner.name should not be preferred over full_name");
    }

    @Test
    void testBuildHtmlTable_ProductUdfReference_UsesName() {
        // product_udf is a list_node whose display value is in "name"
        EntityModel productRef = new EntityModel(Set.of(
                new StringFieldModel("name", "RKYV CSP")
        ));
        EntityModel entity = new EntityModel(Set.of(
                new ReferenceFieldModel("product_udf", productRef)
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("product_udf"), 25);

        assertTrue(html.contains("RKYV CSP"), "product_udf.name should be the cell value");
    }

    @Test
    void testBuildHtmlTable_UnknownReference_FallsBackToName() {
        // An unregistered reference field falls back to DEFAULT extractor (tries "name")
        EntityModel refEntity = new EntityModel(Set.of(
                new StringFieldModel("name", "Some Value")
        ));
        EntityModel entity = new EntityModel(Set.of(
                new ReferenceFieldModel("some_custom_ref", refEntity)
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("some_custom_ref"), 25);

        assertTrue(html.contains("Some Value"), "Unknown reference field should fall back to .name");
    }
}
