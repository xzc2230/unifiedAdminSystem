package org.jeecg.modules.packagemanagement.application.domains.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;

/** Public NS lookup through DNS over HTTPS. A failed lookup is an empty set. */
public final class PublicNameservers {
    private final HttpPort http;

    public PublicNameservers(HttpPort http) { this.http = http; }

    public Set<String> lookup(String apex) {
        try {
            String url = "https://cloudflare-dns.com/dns-query?name="
                    + URLEncoder.encode(apex, StandardCharsets.UTF_8) + "&type=NS";
            HttpPort.Reply reply = http.exchange("GET", url, null, java.util.Map.of("Accept", "application/dns-json"));
            if (!reply.success()) return Set.of();
            return NameserverSet.fromDnsJson(reply.body());
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }
}
