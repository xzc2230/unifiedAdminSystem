package org.jeecg.modules.packagemanagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.jeecg.modules.packagemanagement.application.domains.impl.ClientDomainServiceImpl;
import org.jeecg.modules.packagemanagement.application.domains.support.ClientNginxConfig;
import org.jeecg.modules.packagemanagement.application.domains.support.ClientNginxPublisher;
import org.jeecg.modules.packagemanagement.application.domains.support.CloudflareDomains;
import org.jeecg.modules.packagemanagement.application.domains.support.DomainBatch;
import org.jeecg.modules.packagemanagement.application.domains.support.NameserverSet;
import org.jeecg.modules.packagemanagement.application.domains.support.PublicNameservers;
import org.jeecg.modules.packagemanagement.application.domains.support.RdapExpiration;
import org.jeecg.modules.packagemanagement.application.domains.support.RegistrableApex;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.domain.domain.ClientDomain;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageIdentityRepository;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.ClientDomainMapper;
import org.junit.jupiter.api.Test;

class PackageDomainTest {
    @Test void clientDomainIsAKnownOwnershipTable() {
        assertDoesNotThrow(() -> PackageIdentityRepository.validateType("pkg_client_domain"));
    }

    @Test void batchTokensSplitOnWhitespaceAndKeepTheFirstDuplicate() {
        assertEquals(List.of("vtsqzm.com", "ucawkr.com"), DomainBatch.tokens("vtsqzm.com\r\nucawkr.com"));
        assertEquals(List.of("vtsqzm.com", "ucawkr.com"), DomainBatch.tokens("vtsqzm.com ucawkr.com"));
        assertEquals(List.of("vtsqzm.com", "ucawkr.com"), DomainBatch.tokens("vtsqzm.com\tucawkr.com"));
        assertEquals(List.of("Example.com"), DomainBatch.tokens("Example.com example.com"));
        assertEquals("请填写域名", assertThrows(PackageFailure.class, () -> DomainBatch.tokens(" \n\t")).getMessage());
        StringBuilder many = new StringBuilder();
        for (int index = 0; index < 51; index++) many.append("d").append(index).append(".com ");
        assertEquals("一次最多接入 50 个域名", assertThrows(PackageFailure.class, () -> DomainBatch.tokens(many.toString())).getMessage());
    }

    @Test void apexRejectsSubdomainsAndNormalizesTheRegistrableName() {
        assertEquals("example.com", RegistrableApex.normalize(" Example.COM. "));
        assertEquals("example.co.uk", RegistrableApex.normalize("example.co.uk"));
        assertThrows(IllegalArgumentException.class, () -> RegistrableApex.normalize("www.example.com"));
        assertThrows(IllegalArgumentException.class, () -> RegistrableApex.normalize("www.example.co.uk"));
        assertThrows(IllegalArgumentException.class, () -> RegistrableApex.normalize("https://example.com"));
    }

    @Test void nameserverSetsMatchOnlyTheAssignedPair() {
        String body = "{\"Answer\":[{\"type\":2,\"data\":\"B.ns.cloudflare.com.\"},{\"type\":2,\"data\":\"a.ns.cloudflare.com\"}]}";
        assertTrue(NameserverSet.same("a.ns.cloudflare.com,b.ns.cloudflare.com", NameserverSet.fromDnsJson(body)));
        assertFalse(NameserverSet.same("a.ns.cloudflare.com,b.ns.cloudflare.com", Set.of("a.ns.cloudflare.com")));
        assertEquals(Date.from(Instant.parse("2027-01-02T00:00:00Z")),
                RdapExpiration.parse("{\"events\":[{\"eventAction\":\"expiration\",\"eventDate\":\"2027-01-02T00:00:00Z\"}]}"));
        assertNull(RdapExpiration.parse("{\"events\":[]}"));
    }

    @Test void nginxServesApexAndWildcardOnHttpOnly() {
        String config = ClientNginxConfig.render(List.of("example.com"));
        assertTrue(config.contains("listen 80;"));
        assertFalse(config.contains("listen 443"));
        assertTrue(config.contains("server_name example.com *.example.com;"));
        assertTrue(config.contains("proxy_set_header Host $host;"));
        assertTrue(config.contains("proxy_set_header X-Forwarded-Proto https;"));
        assertTrue(config.contains("/api/v1/attribution/resolve"));
        assertTrue(config.contains("package-legacy"));
        assertFalse(config.contains("return 308"));
        assertEquals("# package client domains: none\n", ClientNginxConfig.render(List.of()));
    }

    @Test void existingCloudflareZoneIsReused() {
        HttpPort http = mock(HttpPort.class);
        PackageManagementProperties settings = configured();
        when(http.exchange(eq("POST"), contains("/zones"), any(), anyMap())).thenReturn(new HttpPort.Reply(400,
                "{\"success\":false,\"errors\":[{\"code\":1061,\"message\":\"already exists\"}]}"));
        when(http.exchange(eq("GET"), contains("/zones?name="), isNull(), anyMap())).thenReturn(new HttpPort.Reply(200,
                "{\"success\":true,\"result\":[{\"id\":\"zone-1\",\"name_servers\":[\"b.ns.cloudflare.com\",\"a.ns.cloudflare.com\"]}]}"));
        CloudflareDomains.Zone zone = new CloudflareDomains(http, settings).open("example.com");
        assertEquals("zone-1", zone.id());
        assertEquals("a.ns.cloudflare.com,b.ns.cloudflare.com", zone.nameservers());
    }

    @Test void pendingDomainStoresNameserversWithoutNginx() {
        ClientDomainMapper mapper = mock(ClientDomainMapper.class);
        CloudflareDomains cloudflare = mock(CloudflareDomains.class);
        PublicNameservers dns = mock(PublicNameservers.class);
        RdapExpiration rdap = mock(RdapExpiration.class);
        ClientNginxPublisher nginx = mock(ClientNginxPublisher.class);
        when(cloudflare.open("example.com")).thenReturn(new CloudflareDomains.Zone("zone-1", "a.ns.cloudflare.com,b.ns.cloudflare.com"));
        when(dns.lookup("example.com")).thenReturn(Set.of("registrar.example"));
        when(rdap.expiration("example.com")).thenReturn(Date.from(Instant.parse("2027-06-01T00:00:00Z")));
        when(mapper.insertClientDomain(any())).thenAnswer(invocation -> { invocation.getArgument(0, ClientDomain.class).setId(8L); return 1; });
        ClientDomainServiceImpl service = service(mapper, cloudflare, dns, rdap, nginx);
        ClientDomain row = new ClientDomain();
        row.setApex("example.com");
        assertEquals(1, service.insertClientDomain(row));
        assertEquals("pending", row.getStatus());
        assertEquals("a.ns.cloudflare.com,b.ns.cloudflare.com", row.getNameservers());
        assertNotNull(row.getExpiresAt());
        verify(nginx, never()).publish(any(), any());
    }

    @Test void matchedNameserversWriteNginxBeforeReady() {
        ClientDomainMapper mapper = mock(ClientDomainMapper.class);
        CloudflareDomains cloudflare = mock(CloudflareDomains.class);
        PublicNameservers dns = mock(PublicNameservers.class);
        ClientNginxPublisher nginx = mock(ClientNginxPublisher.class);
        when(cloudflare.open("example.com")).thenReturn(new CloudflareDomains.Zone("zone-1", "a.ns.cloudflare.com,b.ns.cloudflare.com"));
        when(dns.lookup("example.com")).thenReturn(Set.of("b.ns.cloudflare.com", "a.ns.cloudflare.com"));
        when(nginx.configured(any())).thenReturn(true);
        when(mapper.selectClientDomainByStatus("ready")).thenReturn(List.of());
        when(mapper.insertClientDomain(any())).thenAnswer(invocation -> { invocation.getArgument(0, ClientDomain.class).setId(9L); return 1; });
        ClientDomain row = new ClientDomain();
        row.setApex("example.com");
        service(mapper, cloudflare, dns, mock(RdapExpiration.class), nginx).insertClientDomain(row);
        assertEquals("ready", row.getStatus());
        verify(cloudflare).activate("zone-1", "example.com", "13.222.19.112");
        verify(nginx).publish(any(), eq(List.of("example.com")));
    }

    @Test void matchedNameserversStayUnreadyUntilNginxIsConfigured() {
        ClientDomainMapper mapper = mock(ClientDomainMapper.class);
        CloudflareDomains cloudflare = mock(CloudflareDomains.class);
        PublicNameservers dns = mock(PublicNameservers.class);
        ClientNginxPublisher nginx = mock(ClientNginxPublisher.class);
        when(cloudflare.open("example.com")).thenReturn(new CloudflareDomains.Zone("zone-1", "a.ns.cloudflare.com,b.ns.cloudflare.com"));
        when(dns.lookup("example.com")).thenReturn(Set.of("a.ns.cloudflare.com", "b.ns.cloudflare.com"));
        when(nginx.configured(any())).thenReturn(false);
        when(mapper.insertClientDomain(any())).thenReturn(1);
        ClientDomain row = new ClientDomain();
        row.setApex("example.com");
        service(mapper, cloudflare, dns, mock(RdapExpiration.class), nginx).insertClientDomain(row);
        assertEquals("failed", row.getStatus());
        assertEquals("nginx 未配置", row.getErrorMessage());
        verify(nginx, never()).publish(any(), any());
    }

    @Test void duplicateAndSubdomainAreRejectedBeforeCloudflare() {
        ClientDomainMapper mapper = mock(ClientDomainMapper.class);
        CloudflareDomains cloudflare = mock(CloudflareDomains.class);
        when(mapper.selectClientDomainByApex("example.com")).thenReturn(new ClientDomain());
        ClientDomainServiceImpl service = service(mapper, cloudflare, mock(PublicNameservers.class), mock(RdapExpiration.class), mock(ClientNginxPublisher.class));
        ClientDomain duplicate = new ClientDomain();
        duplicate.setApex("example.com");
        PackageFailure exists = assertThrows(PackageFailure.class, () -> service.insertClientDomain(duplicate));
        assertEquals(400, exists.getCode());
        ClientDomain child = new ClientDomain();
        child.setApex("www.example.com");
        assertThrows(PackageFailure.class, () -> service.insertClientDomain(child));
        verify(cloudflare, never()).open(any());
    }

    @Test void deletingAReadyDomainRewritesNginxWithoutThatName() throws Exception {
        ClientDomainMapper mapper = mock(ClientDomainMapper.class);
        CloudflareDomains cloudflare = mock(CloudflareDomains.class);
        ClientNginxPublisher nginx = new ClientNginxPublisher(command -> assertEquals(List.of("nginx", "-s", "reload"), command));
        PackageManagementProperties settings = configured();
        var file = Files.createTempDirectory("pkg-domains").resolve("package-client-domains.conf");
        settings.setDomainNginxInclude(file.toString());
        settings.setDomainNginxReload("nginx -s reload");
        ClientDomain ready = new ClientDomain();
        ready.setId(3L); ready.setApex("old.example"); ready.setStatus("ready"); ready.setZoneId("zone-old");
        ClientDomain kept = new ClientDomain();
        kept.setId(4L); kept.setApex("keep.example"); kept.setStatus("ready");
        when(mapper.selectClientDomainById(3L)).thenReturn(ready);
        when(mapper.selectClientDomainByStatus("ready")).thenReturn(List.of(ready, kept));
        when(mapper.deleteClientDomainByIds(any())).thenReturn(1);
        new ClientDomainServiceImpl(mapper, cloudflare, mock(PublicNameservers.class), mock(RdapExpiration.class), nginx, settings)
                .deleteClientDomainByIds(new Long[] {3L});
        String written = Files.readString(file);
        assertTrue(written.contains("server_name keep.example *.keep.example;"));
        assertFalse(written.contains("old.example"));
        verify(cloudflare).deleteQuietly("zone-old");
    }

    @Test void publisherRestoresThePreviousFileWhenReloadFails() throws Exception {
        PackageManagementProperties settings = configured();
        var file = Files.createTempDirectory("pkg-domains").resolve("package-client-domains.conf");
        Files.writeString(file, "previous\n", StandardCharsets.UTF_8);
        settings.setDomainNginxInclude(file.toString());
        settings.setDomainNginxReload("nginx -s reload");
        ClientNginxPublisher publisher = new ClientNginxPublisher(command -> { throw new IllegalStateException("nginx 重载失败"); });
        assertThrows(IllegalStateException.class, () -> publisher.publish(settings, List.of("example.com")));
        assertEquals("previous\n", Files.readString(file));
    }

    private static ClientDomainServiceImpl service(ClientDomainMapper mapper, CloudflareDomains cloudflare, PublicNameservers dns,
            RdapExpiration rdap, ClientNginxPublisher nginx) {
        return new ClientDomainServiceImpl(mapper, cloudflare, dns, rdap, nginx, configured());
    }

    private static PackageManagementProperties configured() {
        PackageManagementProperties settings = new PackageManagementProperties();
        settings.setCloudflareApiToken("token");
        settings.setCloudflareAccountId("account");
        settings.setDomainOriginIp("13.222.19.112");
        return settings;
    }
}
