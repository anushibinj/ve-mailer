package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Workspace;
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

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private DynamicMailSenderService dynamicMailSenderService;

    @Mock
    private AiSummaryService aiSummaryService;

    // Use a real registry so extractor behaviour is tested end-to-end.
    private final FieldExtractorRegistry registry = new FieldExtractorRegistry();

    private Workspace testWorkspace;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        testWorkspace = new Workspace();
        testWorkspace.setId(UUID.randomUUID());
        testWorkspace.setSharedSpaceId("4001");
        testWorkspace.setWorkspaceId("5015");
        testWorkspace.setRootUrl("https://ve.example.com");
        lenient().when(dynamicMailSenderService.getMailSender()).thenReturn(mailSender);
        lenient().when(dynamicMailSenderService.getFromAddress()).thenReturn("noreply@test.com");
        notificationService = new NotificationService(dynamicMailSenderService, registry, aiSummaryService);
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
                List.of(sub1, sub2), Collections.emptyList(), List.of("name"), 25, testWorkspace);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void testProcessAndSendNotifications_EmptyList_NoEmailSent() {
        notificationService.processAndSendNotifications(
                Collections.emptyList(), Collections.emptyList(), List.of("name"), 25, testWorkspace);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void testProcessAndSendNotifications_SubjectLineCorrect() throws Exception {
        MimeMessage msg = newMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(msg);

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("check@example.com");

        notificationService.processAndSendNotifications(
                List.of(sub), Collections.emptyList(), List.of("name"), 25, testWorkspace);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("[ve-emailer] Your Notification Digest", captor.getValue().getSubject());
    }

    @Test
    void testProcessAndSendNotifications_AiSummaryEnabled_GeneratesSummaries() {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        when(aiSummaryService.fetchComments(any(), any())).thenReturn("Some comment");
        when(aiSummaryService.generateSummary(any(), any(), any())).thenReturn("AI generated summary");

        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("id", "1001"),
                new StringFieldModel("name", "Fix bug"),
                new StringFieldModel("description", "A bug needs fixing")
        ));

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("user@example.com");

        // Include the AI Summary pseudo-field in the fields list
        List<String> fields = List.of(AiSummaryService.AI_SUMMARY_FIELD, "id", "name", "description");

        notificationService.processAndSendNotifications(
                List.of(sub), List.of(entity), fields, 25, testWorkspace);

        verify(aiSummaryService).generateSummary("Fix bug", "A bug needs fixing", "Some comment");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testProcessAndSendNotifications_AiSummaryDisabled_NoAiCalls() {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());

        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "Fix bug")
        ));

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("user@example.com");

        notificationService.processAndSendNotifications(
                List.of(sub), List.of(entity), List.of("name"), 25, testWorkspace);

        verifyNoInteractions(aiSummaryService);
    }

    // ── buildHtmlTable ────────────────────────────────────────────────────────

    @Test
    void testBuildHtmlTable_EmptyResults_ShowsNoItemsMessage() {
        String html = notificationService.buildHtmlTable(Collections.emptyList(), List.of("name", "phase"), 25, false, null);
        assertTrue(html.contains("No items matched"), "Should show empty-state message");
        assertFalse(html.contains("<table"), "Should not render a table for empty results");
    }

    @Test
    void testBuildHtmlTable_FooterShowsLimit() {
        String html = notificationService.buildHtmlTable(Collections.emptyList(), List.of("name"), 10, false, null);
        assertTrue(html.contains("limited to 10 items"), "Footer should show configured limit");
    }

    @Test
    void testBuildHtmlTable_HeadersMatchFields() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("story_points", "5"),
                new StringFieldModel("phase", "In Progress")
        ));
        String html = notificationService.buildHtmlTable(List.of(entity), List.of("story_points", "phase"), 25, false, null);
        assertTrue(html.contains("Story Points"), "Header should humanise story_points");
        assertTrue(html.contains("Phase"),        "Header should humanise phase");
    }

    @Test
    void testBuildHtmlTable_CellValuesRendered() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "Fix login bug"),
                new StringFieldModel("phase", "Open")
        ));
        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25, false, null);
        assertTrue(html.contains("Fix login bug"), "Cell should contain entity name");
        assertTrue(html.contains("Open"),          "Cell should contain entity phase");
    }

    @Test
    void testBuildHtmlTable_HtmlSpecialCharsEscaped() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "<script>alert('xss')</script>")
        ));
        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name"), 25, false, null);
        assertFalse(html.contains("<script>"), "Raw <script> tag must not appear in output");
        assertTrue(html.contains("&lt;script&gt;"), "Tag must be HTML-escaped");
    }

    @Test
    void testBuildHtmlTable_MissingFieldRendersEmptyCell() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "Some item")
        ));
        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25, false, null);
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

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "phase"), 25, false, null);

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

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "owner"), 25, false, null);

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

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("product_udf"), 25, false, null);

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

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("some_custom_ref"), 25, false, null);

        assertTrue(html.contains("Some Value"), "Unknown reference field should fall back to .name");
    }

    // ── AI Summary in HTML table ──────────────────────────────────────────────

    @Test
    void testBuildHtmlTable_AiSummaryEnabled_AddsFirstColumn() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature"),
                new StringFieldModel("id", "1001")
        ));
        String[] summaries = {"This feature implements login improvements."};

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name", "id"), 25, true, summaries);

        assertTrue(html.contains("AI Summary"), "Header should include AI Summary column");
        assertTrue(html.contains("This feature implements login improvements."), "AI summary text should appear");
    }

    @Test
    void testBuildHtmlTable_AiSummaryDisabled_NoSummaryColumn() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature")
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name"), 25, false, null);

        assertFalse(html.contains("AI Summary"), "Header should not include AI Summary when disabled");
    }

    @Test
    void testBuildHtmlTable_AiSummaryEnabled_NullSummaries_ShowsFallback() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature")
        ));

        String html = notificationService.buildHtmlTable(List.of(entity), List.of("name"), 25, true, null);

        assertTrue(html.contains("AI summary unavailable."), "Should show fallback when summaries array is null");
    }

    @Test
    void testProcessAndSendNotifications_AiSummaryEnabled_WithoutUserDisplayingNameDescription() {
        // Verifies decoupled architecture: backend fetches name/description internally for AI
        // generation even when the user did not select them as display fields.
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        when(aiSummaryService.fetchComments(any(), any())).thenReturn("");
        when(aiSummaryService.generateSummary(any(), any(), any())).thenReturn("AI generated summary");

        // Entity contains name and description because effectiveFetchFields in FilterService
        // added them silently — even though the user only selected id and phase for display.
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("id", "2001"),
                new StringFieldModel("name", "Fix bug"),
                new StringFieldModel("description", "A bug needs fixing"),
                new StringFieldModel("phase", "In Testing")
        ));

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("user@example.com");

        // User's display selection intentionally excludes name and description
        List<String> fields = List.of(AiSummaryService.AI_SUMMARY_FIELD, "id", "phase");

        notificationService.processAndSendNotifications(
                List.of(sub), List.of(entity), fields, 25, testWorkspace);

        // AI summary must still be generated using the entity data fetched internally
        verify(aiSummaryService).generateSummary("Fix bug", "A bug needs fixing", "");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testBuildHtmlTable_AiSummaryEnabled_OnlyRendersUserSelectedColumns() {
        // name and description are in the entity (fetched internally) but not in displayFields
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("id", "2001"),
                new StringFieldModel("name", "Bug"),
                new StringFieldModel("description", "A bug"),
                new StringFieldModel("phase", "In Testing")
        ));
        String[] summaries = {"Quick summary."};

        // displayFields contains only user-chosen columns (no name, no description)
        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("id", "phase"), 25, true, summaries);

        assertTrue(html.contains("AI Summary"), "AI Summary column header must be present");
        assertTrue(html.contains("Quick summary."), "AI summary text must appear in the row");
        assertTrue(html.contains("2001"), "id value must appear");
        assertTrue(html.contains("In Testing"), "phase value must appear");
        assertFalse(html.contains(">Description<") || html.contains(">description<"),
                "Description must not be a visible column header");
    }

    // --- sanitizeAiHtml: HTML rendering tests ---

    @Test
    void testSanitizeAiHtml_AnchorTagPreserved() {
        // Anchor tags produced by the LLM must survive sanitization so they are
        // clickable inside the email body.
        String input = "<a href=\"mailto:user@example.com\">@User Name</a> is waiting.";
        String result = notificationService.sanitizeAiHtml(input);
        assertTrue(result.contains("<a"), "Anchor tag must be preserved");
        assertTrue(result.contains("href=\"mailto:user@example.com\""), "mailto href must be preserved");
        assertTrue(result.contains("@User Name"), "Link text must be preserved");
    }

    @Test
    void testSanitizeAiHtml_ScriptTagStripped() {
        // Script tags must be removed to prevent XSS in email clients that render HTML.
        String input = "<script>alert('xss')</script>Waiting for review.";
        String result = notificationService.sanitizeAiHtml(input);
        assertFalse(result.contains("<script>"), "script tag must be stripped by sanitizer");
        assertTrue(result.contains("Waiting for review."), "Safe text must be retained");
    }

    @Test
    void testSanitizeAiHtml_AllowedFormattingTagsPreserved() {
        String input = "<b>Status:</b> <em>Waiting</em><br>2 days elapsed.";
        String result = notificationService.sanitizeAiHtml(input);
        assertTrue(result.contains("<b>"), "Bold tag must be preserved");
        assertTrue(result.contains("<em>"), "Em tag must be preserved");
        assertTrue(result.contains("<br>") || result.contains("<br />"), "br tag must be preserved");
    }

    @Test
    void testSanitizeAiHtml_NullAndEmptyReturnEmpty() {
        assertEquals("", notificationService.sanitizeAiHtml(null));
        assertEquals("", notificationService.sanitizeAiHtml(""));
    }

    @Test
    void testBuildHtmlTable_AiSummaryHtmlAnchorNotEscaped() {
        // AI-generated anchor tags must appear as raw HTML in the table cell,
        // not as escaped entities like &lt;a href...&gt;
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature"),
                new StringFieldModel("id", "1001")
        ));
        String anchorSummary = "<a href=\"mailto:dev@example.com\">@Dev</a> is waiting.";
        String[] summaries = {anchorSummary};

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("name"), 25, true, summaries);

        assertFalse(html.contains("&lt;a"), "Anchor tag must NOT be HTML-escaped in the AI summary cell");
        assertTrue(html.contains("<a"), "Anchor tag must render as raw HTML in the AI summary cell");
        assertTrue(html.contains("@Dev"), "Link text must be present");
    }

    @Test
    void testBuildHtmlTable_AiSummaryScriptTagSanitized() {
        // Even if the LLM returns a dangerous tag (e.g. due to prompt injection),
        // it must be stripped before the email is sent.
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("name", "My Feature")
        ));
        String maliciousSummary = "<script>alert('xss')</script>Ticket is done.";
        String[] summaries = {maliciousSummary};

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("name"), 25, true, summaries);

        assertFalse(html.contains("<script>"), "script tag must be stripped from AI summary");
        assertTrue(html.contains("Ticket is done."), "Safe summary text must remain");
    }

    // ── ticket hyperlink generation ───────────────────────────────────────────

    @Test
    void testBuildHtmlTable_IdField_RenderedAsHyperlink() {
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("id", "12345"),
                new StringFieldModel("name", "Login Bug")
        ));
        NotificationService.TicketLinkContext ctx =
                new NotificationService.TicketLinkContext("https://ve.example.com", "4001", "5015");

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("id", "name"), 25, false, null, ctx);

        assertTrue(html.contains("href="), "id field must be rendered as a hyperlink");
        assertTrue(html.contains("12345"), "id value must appear as link text");
        assertTrue(html.contains("ve.example.com"), "server URL must appear in the link");
        assertTrue(html.contains("id=12345"), "ticket id must appear in the URL");
    }

    @Test
    void testBuildHtmlTable_IdField_HyperlinkUrlFormat() {
        // Verify the exact URL structure for id hyperlinks.
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("id", "99")));
        NotificationService.TicketLinkContext ctx =
                new NotificationService.TicketLinkContext("https://ve.example.com", "4001", "5015");

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("id"), 25, false, null, ctx);

        // URL must follow: serverUrl/ui/?p=sharedSpaceId/workspaceId#/entity-navigation?entityType=work_item&id=ticketId
        assertTrue(html.contains("/ui/?p=4001/5015"), "URL must contain shared-space and workspace IDs");
        assertTrue(html.contains("entityType=work_item"), "URL must specify entity type");
        // & in the HTML href attribute must be encoded as &amp;
        assertTrue(html.contains("&amp;id=99"), "& before id param must be HTML-escaped as &amp; in href");
    }

    @Test
    void testBuildHtmlTable_GlobalIdUdf_RenderedAsHyperlinkUsingInternalId() {
        // global_id_udf displays the global identifier text but the URL must use the internal id.
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("id", "12345"),
                new StringFieldModel("global_id_udf", "OCTCR77BD384588")
        ));
        NotificationService.TicketLinkContext ctx =
                new NotificationService.TicketLinkContext("https://ve.example.com", "4001", "5015");

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("global_id_udf"), 25, false, null, ctx);

        assertTrue(html.contains("OCTCR77BD384588"), "global_id_udf value must be the link text");
        assertTrue(html.contains("id=12345"), "URL must use the internal numeric id, not global_id_udf");
        assertTrue(html.contains("href="), "global_id_udf must be wrapped in an anchor");
    }

    @Test
    void testBuildHtmlTable_HyperlinkNotRenderedWithoutLinkContext() {
        // When linkContext is null (backward-compatible 5-arg call), id renders as plain text.
        EntityModel entity = new EntityModel(Set.of(new StringFieldModel("id", "12345")));

        // 5-arg overload — no link context
        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("id"), 25, false, null);

        assertFalse(html.contains("href="), "id must NOT be a hyperlink when linkContext is null");
        assertTrue(html.contains("12345"), "id value must still appear as plain text");
    }

    @Test
    void testBuildHtmlTable_HyperlinkLabel_IsHtmlEscaped() {
        // The label text is HTML-escaped before insertion even for hyperlinked fields.
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("id", "1"),
                new StringFieldModel("global_id_udf", "<b>XSS</b>")
        ));
        NotificationService.TicketLinkContext ctx =
                new NotificationService.TicketLinkContext("https://ve.example.com", "4001", "5015");

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("global_id_udf"), 25, false, null, ctx);

        assertFalse(html.contains("<b>XSS</b>"), "label must be HTML-escaped inside the anchor");
        assertTrue(html.contains("&lt;b&gt;XSS&lt;/b&gt;"), "label special chars must be escaped");
    }

    @Test
    void testBuildHtmlTable_IdMissing_HyperlinkEligibleFieldFallsBackToEscapedText() {
        // When the entity has no id field (should not happen after FilterService change,
        // but must be safe), the hyperlink falls back to plain escaped text.
        EntityModel entity = new EntityModel(Set.of(
                new StringFieldModel("global_id_udf", "OCTCR-ABC")
        ));
        NotificationService.TicketLinkContext ctx =
                new NotificationService.TicketLinkContext("https://ve.example.com", "4001", "5015");

        String html = notificationService.buildHtmlTable(
                List.of(entity), List.of("global_id_udf"), 25, false, null, ctx);

        assertFalse(html.contains("href="), "without id, global_id_udf must not be a hyperlink");
        assertTrue(html.contains("OCTCR-ABC"), "value must still appear as text");
    }
}
