package org.jeecg.config.shiro;

import org.apache.shiro.mgt.SecurityManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AttributionShiroBoundaryTest {
    @Test
    void onlyResolveUsesIndependentAuthenticationAndManagementRemainsProtected() {
        ShiroConfig config = new ShiroConfig();
        ReflectionTestUtils.setField(config, "env", mock(Environment.class));
        var chains = config.shiroFilter(mock(SecurityManager.class)).getFilterChainDefinitionMap();
        assertEquals("anon", chains.get("/api/v1/attribution/resolve"));
        assertEquals("jwt", chains.get("/**"));
        assertFalse(chains.containsKey("/api/v1/attribution/**"));
        assertFalse(chains.containsKey("/attribution-admin/**"));
        assertTrue(chains.keySet().stream().toList().indexOf("/api/v1/attribution/resolve")
                < chains.keySet().stream().toList().indexOf("/**"));
    }
}
