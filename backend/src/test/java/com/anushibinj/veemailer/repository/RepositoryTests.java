package com.anushibinj.veemailer.repository;

import com.anushibinj.veemailer.model.EmailSubscriber;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.OtpRequest;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RepositoryTests {

    @Autowired
    private EmailSubscriberRepository emailSubscriberRepository;

    @Autowired
    private OtpRequestRepository otpRequestRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private FilterRepository filterRepository;

    @Test
    void testEmailSubscriberQueries() {
        Workspace w = new Workspace();
        w.setTitle("Workspace 1");
        workspaceRepository.save(w);

        Filter f = new Filter();
        f.setTitle("Filter 1");
        f.setWorkspace(w);
        filterRepository.save(f);

        EmailSubscriber sub1 = new EmailSubscriber();
        sub1.setRecipientEmail("user1@example.com");
        sub1.setWorkspace(w);
        sub1.setFilter(f);
        sub1.setFrequency(Frequency.DAILY);
        sub1.setStatus(Status.ACTIVE);
        emailSubscriberRepository.save(sub1);

        EmailSubscriber sub2 = new EmailSubscriber();
        sub2.setRecipientEmail("user2@example.com");
        sub2.setWorkspace(w);
        sub2.setFilter(f);
        sub2.setFrequency(Frequency.WEEKLY);
        sub2.setStatus(Status.ACTIVE);
        emailSubscriberRepository.save(sub2);

        // Test custom query
        List<EmailSubscriber> dailyActive = emailSubscriberRepository.findByFrequencyAndStatus(Frequency.DAILY, Status.ACTIVE);
        assertEquals(1, dailyActive.size());
        assertEquals("user1@example.com", dailyActive.get(0).getRecipientEmail());

        Optional<EmailSubscriber> found = emailSubscriberRepository
                .findByRecipientEmailAndWorkspaceIdAndFilterId("user2@example.com", w.getId(), f.getId());
        assertTrue(found.isPresent());
        assertEquals(Frequency.WEEKLY, found.get().getFrequency());

        List<EmailSubscriber> workspaceActive = emailSubscriberRepository.findByWorkspaceIdAndStatus(w.getId(), Status.ACTIVE);
        assertEquals(2, workspaceActive.size());
    }

    @Test
    void testOtpRequestQueries() {
        OtpRequest req = new OtpRequest();
        req.setEmail("test@test.com");
        req.setExpiresAt(LocalDateTime.now().minusMinutes(5)); // expired 5 mins ago
        otpRequestRepository.save(req);

        OtpRequest req2 = new OtpRequest();
        req2.setEmail("active@test.com");
        req2.setExpiresAt(LocalDateTime.now().plusMinutes(5)); // expires in 5 mins
        otpRequestRepository.save(req2);

        Optional<OtpRequest> found = otpRequestRepository.findByEmail("active@test.com");
        assertTrue(found.isPresent());

        // Test deletion
        otpRequestRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        Optional<OtpRequest> expired = otpRequestRepository.findByEmail("test@test.com");
        assertFalse(expired.isPresent()); // Should be deleted

        Optional<OtpRequest> stillActive = otpRequestRepository.findByEmail("active@test.com");
        assertTrue(stillActive.isPresent()); // Should NOT be deleted
    }

    @Test
    void testFindByRecipientEmail_ReturnsMatchingSubscribers() {
        Workspace w = new Workspace();
        w.setTitle("Workspace For RecipientEmail Test");
        workspaceRepository.save(w);

        Filter f = new Filter();
        f.setTitle("Filter For RecipientEmail Test");
        f.setWorkspace(w);
        filterRepository.save(f);

        EmailSubscriber sub = new EmailSubscriber();
        sub.setRecipientEmail("findme@example.com");
        sub.setWorkspace(w);
        sub.setFilter(f);
        sub.setFrequency(Frequency.HOURLY);
        sub.setStatus(Status.ACTIVE);
        emailSubscriberRepository.save(sub);

        List<EmailSubscriber> results = emailSubscriberRepository.findByRecipientEmail("findme@example.com");
        assertEquals(1, results.size());
        assertEquals("findme@example.com", results.get(0).getRecipientEmail());
    }

    @Test
    void testFindByRecipientEmail_NotFound_ReturnsEmpty() {
        List<EmailSubscriber> results = emailSubscriberRepository.findByRecipientEmail("nobody@example.com");
        assertTrue(results.isEmpty());
    }

    @Test
    void testFindByEmail_NotFound_ReturnsEmpty() {
        Optional<OtpRequest> result = otpRequestRepository.findByEmail("nonexistent@example.com");
        assertFalse(result.isPresent());
    }
}
