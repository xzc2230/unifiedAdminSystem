package org.jeecg.modules.packagemanagement.application.domains.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jeecg.modules.packagemanagement.application.domains.IClientDomainService;
import org.jeecg.modules.packagemanagement.application.domains.support.ClientNginxPublisher;
import org.jeecg.modules.packagemanagement.application.domains.support.CloudflareDomains;
import org.jeecg.modules.packagemanagement.application.domains.support.NameserverSet;
import org.jeecg.modules.packagemanagement.application.domains.support.PublicNameservers;
import org.jeecg.modules.packagemanagement.application.domains.support.RdapExpiration;
import org.jeecg.modules.packagemanagement.application.domains.support.RegistrableApex;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.domain.domain.ClientDomain;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.ClientDomainMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClientDomainServiceImpl implements IClientDomainService {
    public static final String PENDING = "pending";
    public static final String READY = "ready";
    public static final String FAILED = "failed";
    private static final Logger log = LoggerFactory.getLogger(ClientDomainServiceImpl.class);
    private final ClientDomainMapper domains;
    private final CloudflareDomains cloudflare;
    private final PublicNameservers dns;
    private final RdapExpiration rdap;
    private final ClientNginxPublisher nginx;
    private final PackageManagementProperties settings;
    private final Object gate = new Object();

    @Autowired
    public ClientDomainServiceImpl(ClientDomainMapper domains, HttpPort http, PackageManagementProperties settings) {
        this(domains, new CloudflareDomains(http, settings), new PublicNameservers(http), new RdapExpiration(http), new ClientNginxPublisher(), settings);
    }

    public ClientDomainServiceImpl(ClientDomainMapper domains, CloudflareDomains cloudflare, PublicNameservers dns,
            RdapExpiration rdap, ClientNginxPublisher nginx, PackageManagementProperties settings) {
        this.domains = domains;
        this.cloudflare = cloudflare;
        this.dns = dns;
        this.rdap = rdap;
        this.nginx = nginx;
        this.settings = settings;
    }

    @Override public ClientDomain selectClientDomainById(Long id) { return domains.selectClientDomainById(id); }
    @Override public List<ClientDomain> selectClientDomainList(ClientDomain filter) { return domains.selectClientDomainList(filter); }

    @Override
    public int insertClientDomain(ClientDomain row) {
        synchronized (gate) {
            String apex;
            try { apex = RegistrableApex.normalize(row.getApex()); }
            catch (IllegalArgumentException failure) { throw new PackageFailure(400, failure.getMessage()); }
            if (domains.selectClientDomainByApex(apex) != null) throw new PackageFailure(400, "域名已存在");
            Date now = new Date();
            row.setApex(apex);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            CloudflareDomains.Zone zone = cloudflare.open(apex);
            row.setZoneId(zone.id());
            row.setNameservers(zone.nameservers());
            row.setExpiresAt(rdap.expiration(apex));
            settle(row);
            return domains.insertClientDomain(row);
        }
    }

    @Override
    public int updateClientDomain(ClientDomain row) {
        ClientDomain stored = domains.selectClientDomainById(row.getId());
        if (stored == null) return 0;
        try {
            if (!RegistrableApex.normalize(row.getApex()).equals(stored.getApex())) throw new PackageFailure(400, "域名不能修改");
        } catch (IllegalArgumentException failure) {
            throw new PackageFailure(400, "域名不能修改");
        }
        return 1;
    }

    @Override
    public int deleteClientDomainByIds(Long[] ids) {
        synchronized (gate) {
            List<ClientDomain> rows = new ArrayList<>();
            if (ids != null) for (Long id : ids) {
                ClientDomain row = domains.selectClientDomainById(id);
                if (row != null) rows.add(row);
            }
            boolean readyRemoved = rows.stream().anyMatch(row -> READY.equals(row.getStatus()));
            if (readyRemoved && nginx.configured(settings)) {
                Set<Long> removing = rows.stream().map(ClientDomain::getId).collect(Collectors.toSet());
                List<ClientDomain> ready = domains.selectClientDomainByStatus(READY);
                List<String> remain = new ArrayList<>();
                if (ready != null) for (ClientDomain row : ready) if (!removing.contains(row.getId())) remain.add(row.getApex());
                nginx.publish(settings, remain);
            }
            int affected = domains.deleteClientDomainByIds(ids);
            for (ClientDomain row : rows) cloudflare.deleteQuietly(row.getZoneId());
            return affected;
        }
    }

    @Override
    public void syncPending() {
        synchronized (gate) {
            List<ClientDomain> rows = domains.selectClientDomainByStatuses(List.of(PENDING, FAILED));
            if (rows == null) return;
            for (ClientDomain row : rows) {
                try {
                    Date refreshed = rdap.expiration(row.getApex());
                    if (refreshed != null) row.setExpiresAt(refreshed);
                    settle(row);
                    row.setUpdateTime(new Date());
                    domains.updateClientDomain(row);
                } catch (RuntimeException failure) {
                    log.warn("Client domain sync failed for {}", row.getApex(), failure);
                }
            }
        }
    }

    private void settle(ClientDomain row) {
        if (!NameserverSet.same(row.getNameservers(), dns.lookup(row.getApex()))) {
            row.setStatus(PENDING);
            row.setErrorMessage(null);
            return;
        }
        try {
            cloudflare.activate(row.getZoneId(), row.getApex(), origin());
            if (!nginx.configured(settings)) {
                row.setStatus(FAILED);
                row.setErrorMessage("nginx 未配置");
                return;
            }
            List<String> names = new ArrayList<>();
            List<ClientDomain> ready = domains.selectClientDomainByStatus(READY);
            if (ready != null) for (ClientDomain item : ready) if (!row.getApex().equals(item.getApex())) names.add(item.getApex());
            names.add(row.getApex());
            nginx.publish(settings, names);
            row.setStatus(READY);
            row.setErrorMessage(null);
        } catch (RuntimeException failure) {
            row.setStatus(FAILED);
            row.setErrorMessage(limit(failure.getMessage()));
        }
    }

    private String origin() {
        String configured = settings.getDomainOriginIp();
        String value = configured == null || configured.isBlank() ? "13.222.19.112" : configured.trim();
        String[] parts = value.split("\\.");
        if (parts.length != 4) throw new PackageFailure(400, "源站 IP 无效");
        for (String part : parts) {
            int octet;
            try { octet = Integer.parseInt(part); }
            catch (NumberFormatException failure) { throw new PackageFailure(400, "源站 IP 无效"); }
            if (octet < 0 || octet > 255 || !part.equals(Integer.toString(octet))) throw new PackageFailure(400, "源站 IP 无效");
        }
        return value;
    }

    private static String limit(String message) {
        if (message == null || message.isBlank()) return "接入失败";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
