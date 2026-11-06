package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceAccessTest {
    private final ResourceRepository repository = mock(ResourceRepository.class);
    private final ResourceAccess access = new ResourceAccess(repository);

    @Test void selectedTenantMustBeAnActiveMembershipNotJustAHeader() {
        assertThrows(ResourceFailure.class, () -> access.resolve("user-a", "200"));
        verify(repository).activeMember(200, "user-a");
    }

    @Test void requiresAnExplicitTenantAndLoggedInUser() {
        assertThrows(ResourceFailure.class, () -> access.resolve("user-a", null));
        assertThrows(ResourceFailure.class, () -> access.resolve(null, "100"));
        assertThrows(ResourceFailure.class, () -> access.resolve("user-a", "1 OR 1=1"));
        verifyNoInteractions(repository);
    }

    @Test void resolvesOnlyTheVerifiedMembership() {
        when(repository.activeMember(100, "user-a")).thenReturn(true);
        assertEquals(new ResourceAccess.Actor(100, "user-a"), access.resolve("user-a", "100"));
    }
}
