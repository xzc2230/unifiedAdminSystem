package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceServiceTest {
    private final ResourceRepository repository = mock(ResourceRepository.class);
    private final ResourceAccess access = mock(ResourceAccess.class);
    private final DnsTxtResolver dns = mock(DnsTxtResolver.class);
    private final ResourceService service = new ResourceService(repository, access, dns);
    private final ResourceAccess.Actor actor = new ResourceAccess.Actor(100, "user-a");

    @BeforeEach void actor() { when(access.current()).thenReturn(actor); }

    private void siteRole(String role) {
        when(repository.site(100, "site-a", false)).thenReturn(Map.of("id", "site-a"));
        when(repository.site(100, "site-a", true)).thenReturn(Map.of("id", "site-a"));
        when(repository.memberRole(100, "site-a", "user-a")).thenReturn(role);
        when(repository.activeMemberCurrent(100, "user-a")).thenReturn(true);
    }

    @Test void revokedTenantMembershipIsRecheckedAfterSiteLockBeforeReadingRoleOrWriting() {
        siteRole("EDITOR");
        when(repository.activeMemberCurrent(100, "user-a")).thenReturn(false);
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service.deleteTarget("site-a", "target")).status());
        var order = inOrder(repository);
        order.verify(repository).site(100, "site-a", true);
        order.verify(repository).activeMemberCurrent(100, "user-a");
        verify(repository, never()).memberRole(anyInt(), anyString(), anyString());
        verify(repository, never()).liveTargetReferenced(anyInt(), anyString(), anyString());
        verify(repository, never()).deleteTarget(anyInt(), anyString(), anyString());
    }

    @Test void foreignSiteIdIsRejectedBeforeReadingChildren() {
        assertThrows(ResourceFailure.class, () -> service.targets("foreign-site"));
        verify(repository, never()).targets(anyInt(), anyString());
    }

    @Test void viewerCannotCreateTarget() {
        siteRole("VIEWER");
        assertThrows(ResourceFailure.class, () -> service.addTarget("site-a", new ResourceRequests.TargetInput("Main", "https://example.com", true)));
        verify(repository, never()).insertTarget(anyInt(), anyString(), any(), anyString());
    }

    @Test void editorCannotGrantMembership() {
        siteRole("EDITOR");
        assertThrows(ResourceFailure.class, () -> service.putMember("site-a", new ResourceRequests.MemberInput("user-b", "ADMIN")));
        verify(repository, never()).upsertMember(anyInt(), anyString(), anyString(), anyString());
    }

    @Test void adminCannotAddForeignTenantUserOrDemoteSelf() {
        siteRole("ADMIN");
        assertThrows(ResourceFailure.class, () -> service.putMember("site-a", new ResourceRequests.MemberInput("user-b", "VIEWER")));
        assertThrows(ResourceFailure.class, () -> service.putMember("site-a", new ResourceRequests.MemberInput("user-a", "VIEWER")));
        verify(repository, never()).upsertMember(anyInt(), anyString(), anyString(), anyString());
    }

    @Test void nestedTargetCannotBeDeletedThroughAnotherSite() {
        siteRole("EDITOR");
        assertThrows(ResourceFailure.class, () -> service.deleteTarget("site-a", "foreign-target"));
        verify(repository).deleteTarget(100, "site-a", "foreign-target");
        verify(repository, never()).audit(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void expiredChallengeDoesNotQueryDns() {
        siteRole("EDITOR");
        when(repository.domain(100, "site-a", "domain-a")).thenReturn(Map.of("hostname", "example.com", "verified", false, "challenge", "token", "challengeValid", false));
        assertThrows(ResourceFailure.class, () -> service.verifyDomain("site-a", "domain-a"));
        verifyNoInteractions(dns);
    }

    @Test void correctDnsRecordStillRequiresAtomicCurrentChallengeCheck() {
        siteRole("EDITOR");
        when(repository.domain(100, "site-a", "domain-a")).thenReturn(Map.of("hostname", "example.com", "verified", false, "challenge", "token", "challengeValid", true));
        when(dns.records("_jeecg-verify.example.com")).thenReturn(Set.of("jeecg-site=token"));
        when(repository.verifyDomain(100, "site-a", "domain-a", "token")).thenReturn(0);
        assertThrows(ResourceFailure.class, () -> service.verifyDomain("site-a", "domain-a"));
        verify(repository, never()).audit(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void pageSizeIsBoundedAndQueryIsActorScoped() {
        service.sites(1, 20);
        verify(repository).sites(actor, 0, 20);
        assertThrows(ResourceFailure.class, () -> service.sites(1, 10000));
    }
}
