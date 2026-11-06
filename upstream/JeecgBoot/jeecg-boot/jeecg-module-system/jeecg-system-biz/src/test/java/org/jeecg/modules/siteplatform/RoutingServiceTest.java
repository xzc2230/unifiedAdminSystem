package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingServiceTest {
    final ResourceRepository resources = mock(ResourceRepository.class);
    final ResourceAccess access = mock(ResourceAccess.class);
    final RoutingRepository repository = mock(RoutingRepository.class);
    final RoutingService service = new RoutingService(repository, resources, access);
    final ResourceAccess.Actor actor = new ResourceAccess.Actor(100, "alice");
    final RoutingRequests.DraftInput input = new RoutingRequests.DraftInput(0, "target", List.of("domain"), List.of());

    @BeforeEach void setup() {
        when(access.current()).thenReturn(actor);
        when(resources.site(100, "site", true)).thenReturn(Map.of("id", "site", "environment", "prod", "enabled", 1));
        when(resources.memberRole(100, "site", "alice")).thenReturn("ADMIN");
        when(repository.activeMemberCurrent(100, "alice")).thenReturn(true);
        when(repository.domains(100, "site")).thenReturn(List.of(Map.of("id", "domain", "hostname", "www.example.com", "verified", 1)));
        when(repository.targets(100, "site")).thenReturn(List.of(Map.of("id", "target", "url", "https://origin.example.com", "enabled", 1)));
    }
    RoutingRepository.Draft draft(long revision, String status, String submitter) {
        return new RoutingRepository.Draft(revision, status, RoutingRules.json(input), null, null, null, submitter, null, null);
    }
    @Test void staleRevisionDoesNotWriteOrAudit() {
        when(repository.draft(100, "site")).thenReturn(draft(3, "DRAFT", null));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> service.save("site", input)).status());
        verify(repository, never()).save(anyInt(), anyString(), any());
        verify(resources, never()).audit(any(), anyString(), anyString(), anyString(), anyString());
    }
    @Test void productionSubmitterCannotApproveOwnCandidate() {
        when(repository.draft(100, "site")).thenReturn(draft(1, "SUBMITTED", "alice"));
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service.approve("site", 1)).status());
        verify(repository, never()).save(anyInt(), anyString(), any());
    }
    @Test void onlyAdminCanApproveOrPublishAndViewerCannotSave() {
        for (String role : List.of("EDITOR", "VIEWER")) {
            when(resources.memberRole(100, "site", "alice")).thenReturn(role);
            assertEquals(403, assertThrows(ResourceFailure.class, () -> service.approve("site", 0)).status());
            assertEquals(403, assertThrows(ResourceFailure.class, () -> service.publish("site", 0)).status());
        }
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service.save("site", input)).status());
    }
    @Test void currentMembershipCheckedAfterSiteLockAndBeforeChildren() {
        when(repository.activeMemberCurrent(100, "alice")).thenReturn(false);
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service.view("site")).status());
        var order = inOrder(resources, repository);
        order.verify(resources).site(100, "site", true);
        order.verify(repository).activeMemberCurrent(100, "alice");
        verify(repository, never()).draft(anyInt(), anyString());
    }
    @Test void inaccessibleSiteNeverReadsDraftOrTargets() {
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service.view("foreign")).status());
        verify(repository, never()).draft(anyInt(), anyString());
        verify(repository, never()).targets(anyInt(), anyString());
    }
    @Test void submissionRejectsUnverifiedDomainAndDisabledTarget() {
        when(repository.draft(100, "site")).thenReturn(draft(0, "DRAFT", null));
        when(repository.domains(100, "site")).thenReturn(List.of(Map.of("id", "domain", "hostname", "www.example.com", "verified", 0)));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> service.submit("site", 0)).status());
        when(repository.domains(100, "site")).thenReturn(List.of(Map.of("id", "domain", "hostname", "www.example.com", "verified", 1)));
        when(repository.targets(100, "site")).thenReturn(List.of(Map.of("id", "target", "url", "https://origin.example.com", "enabled", 0)));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> service.submit("site", 0)).status());
    }
    @Test void editingClearsCandidateAndApproval() {
        when(repository.draft(100, "site")).thenReturn(draft(0, "APPROVED", "bob"));
        assertEquals(1, service.save("site", input));
        var captor = org.mockito.ArgumentCaptor.forClass(RoutingRepository.Draft.class);
        verify(repository).save(eq(100), eq("site"), captor.capture());
        var saved = captor.getValue();
        assertEquals("DRAFT", saved.status());
        assertNull(saved.payload()); assertNull(saved.hash()); assertNull(saved.approvedBy()); assertNull(saved.submittedBy());
    }
}
