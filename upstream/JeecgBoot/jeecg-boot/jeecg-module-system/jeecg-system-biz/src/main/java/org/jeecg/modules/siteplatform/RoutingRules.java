package org.jeecg.modules.siteplatform;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class RoutingRules {
    private RoutingRules() {}
    public static boolean flag(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() == 1; }
    public static boolean matches(String rule, String type, String path) {
        return "EXACT".equals(type) ? rule.equals(path) : "PREFIX".equals(type) &&
                (rule.equals("/") || path.equals(rule) || path.startsWith((rule.endsWith("/") ? rule.substring(0, rule.length() - 1) : rule) + "/"));
    }
    public static String path(String value) {
        if (value == null || value.length() > 2048 || !value.startsWith("/") || value.contains("//") ||
                value.chars().anyMatch(c -> c <= 32 || c >= 127 || c == '\\' || c == '?' || c == '#')) throw ResourceRules.invalid("路径格式无效");
        try {
            URI uri = URI.create(value);
            // Reject ambiguous encodings and dot segments; rules and simulation use the same raw path.
            String decoded = uri.getPath();
            if (!uri.isAbsolute() && uri.getRawAuthority() == null && decoded != null &&
                    !decoded.contains("\\") && !decoded.contains("//") && !decoded.matches(".*(?:^|/)\\.{1,2}(?:/|$).*") &&
                    !value.toLowerCase(Locale.ROOT).matches(".*%(?:2e|2f|5c|00|0a|0d).*")) {
                var matcher = java.util.regex.Pattern.compile("%[0-9a-fA-F]{2}").matcher(value);
                return matcher.replaceAll(result -> result.group().toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) { }
        throw ResourceRules.invalid("路径格式无效");
    }
    public static RoutingRequests.DraftInput normalize(RoutingRequests.DraftInput input) {
        if (input == null || input.revision() < 0 || input.revision() >= 9007199254740991L || !identifier(input.defaultTargetId()) ||
                input.domainIds() == null || input.domainIds().isEmpty() || input.domainIds().size() > 100 ||
                input.rules() == null || input.rules().size() > 100) throw ResourceRules.invalid("草稿格式无效");
        Set<String> domains = new TreeSet<>();
        for (String id : input.domainIds()) if (!identifier(id) || !domains.add(id)) throw ResourceRules.invalid("域名标识无效或重复");
        Set<String> ids = new HashSet<>(); Set<Integer> priorities = new HashSet<>();
        List<RoutingRequests.RuleInput> rules = new ArrayList<>();
        for (var rule : input.rules()) {
            if (rule == null || !identifier(rule.id()) || !ids.add(rule.id()) || rule.priority() == null || rule.priority() < 0 || rule.priority() > 1000000 || !priorities.add(rule.priority()) ||
                    !Set.of("EXACT", "PREFIX").contains(rule.matchType() == null ? "" : rule.matchType()) || !identifier(rule.targetId()) ||
                    rule.domainId() != null && !domains.contains(rule.domainId())) throw ResourceRules.invalid("规则标识、域名、优先级或类型无效");
            rules.add(new RoutingRequests.RuleInput(rule.id(), rule.domainId(), path(rule.path()), rule.matchType(), rule.priority(), rule.targetId()));
        }
        rules.sort(Comparator.comparing(RoutingRequests.RuleInput::priority).reversed().thenComparing(RoutingRequests.RuleInput::id));
        return new RoutingRequests.DraftInput(input.revision(), input.defaultTargetId(), List.copyOf(domains), List.copyOf(rules));
    }
    public static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,32}"); }
    public static String json(Object value) { return JSON.toJSONString(value, JSONWriter.Feature.WriteMapNullValue); }
    public static RoutingRequests.DraftInput input(String json) {
        try { return normalize(JSON.parseObject(json, RoutingRequests.DraftInput.class)); }
        catch (RuntimeException e) { throw new ResourceFailure(409, "草稿内容无效，请重新保存"); }
    }
    public static String hash(String payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    static Map<String, Object> object(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
    public record Candidate(String payload, String hash, String resourceState) {}

    /** Caller has locked the site and reads these resources with current, locking reads. */
    public static Candidate candidate(int tenant, String site, Map<String,Object> siteRow, RoutingRequests.DraftInput raw,
                                      List<Map<String,Object>> domainRows, List<Map<String,Object>> targetRows, String releaseId) {
        var input = normalize(raw);
        if (!flag(siteRow.get("enabled"))) throw new ResourceFailure(409, "站点未启用");
        Map<String, Map<String,Object>> domains = index(domainRows), targets = index(targetRows);
        List<String> hosts = new ArrayList<>(); List<Object> domainState = new ArrayList<>();
        for (String id : input.domainIds()) {
            var row = domains.get(id);
            if (row == null || !flag(row.get("verified"))) throw unavailable();
            String host = ResourceRules.hostname(row.get("hostname").toString()); hosts.add(host);
            domainState.add(object("id", id, "hostname", host, "verifiedAt", Objects.toString(row.get("verifiedAt"), "")));
        }
        Collections.sort(hosts);
        TreeSet<String> targetIds = new TreeSet<>(); targetIds.add(input.defaultTargetId());
        input.rules().forEach(rule -> targetIds.add(rule.targetId()));
        List<Object> targetState = new ArrayList<>(); Map<String,Object> targetViews = new HashMap<>();
        for (String id : targetIds) {
            var row = targets.get(id);
            if (row == null || !flag(row.get("enabled"))) throw unavailable();
            String url = targetUrl(row.get("url").toString());
            if (hosts.contains(URI.create(url).getHost())) throw ResourceRules.invalid("路由目标不能指向本站发布域名");
            targetViews.put(id, object("id", id, "url", url));
            targetState.add(object("id", id, "url", url, "updatedAt", Objects.toString(row.get("updatedAt"), "")));
        }
        List<Object> rules = new ArrayList<>();
        for (var rule : input.rules()) rules.add(object("id", rule.id(), "hostname", rule.domainId() == null ? null : domains.get(rule.domainId()).get("hostname"),
                "path", rule.path(), "matchType", rule.matchType(), "priority", rule.priority(), "target", targetViews.get(rule.targetId())));
        String payload = json(object("schemaVersion", 1, "tenantId", tenant, "siteId", site, "environment", siteRow.get("environment"),
                "releaseId", releaseId, "domains", hosts, "defaultTarget", targetViews.get(input.defaultTargetId()), "rules", rules));
        return new Candidate(payload, hash(payload), json(object("domains", domainState, "targets", targetState)));
    }
    private static Map<String, Map<String,Object>> index(List<Map<String,Object>> rows) {
        Map<String,Map<String,Object>> index = new HashMap<>();
        for (var row : rows) index.put(row.get("id").toString(), row);
        return index;
    }
    static ResourceFailure unavailable() { return new ResourceFailure(409, "引用资源不可用或已变更，请刷新后重新提交"); }
    public static String targetUrl(String value) {
        String normalized = ResourceRules.targetUrl(value);
        URI uri = URI.create(normalized);
        if (uri.getRawQuery() != null) throw ResourceRules.invalid("路由目标不能包含查询参数");
        String base = uri.getRawPath();
        return uri.getScheme() + "://" + uri.getRawAuthority() + (base == null || base.isEmpty() ? "" : path(base));
    }
    /** Global policy: chaining any platform-hosted domain is forbidden, even across nodes/tenants. */
    public static void validateHostedSet(String candidate, List<String> otherLivePayloads) {
        try {
            List<String> payloads = new ArrayList<>(otherLivePayloads); payloads.add(candidate);
            Set<String> domains = new HashSet<>(); List<String> upstreams = new ArrayList<>();
            for (String payload : payloads) {
                var data = JSON.parseObject(payload);
                for (Object domain : data.getJSONArray("domains")) if (!domains.add(domain.toString())) throw hostedConflict();
                upstreams.add(URI.create(data.getJSONObject("defaultTarget").getString("url")).getHost());
                for (Object item : data.getJSONArray("rules")) {
                    var rule = (com.alibaba.fastjson2.JSONObject) item;
                    upstreams.add(URI.create(rule.getJSONObject("target").getString("url")).getHost());
                }
            }
            if (upstreams.stream().anyMatch(host -> host == null || domains.contains(host))) throw hostedConflict();
        } catch (RuntimeException e) { throw hostedConflict(); }
    }
    private static ResourceFailure hostedConflict() { return new ResourceFailure(409, "发布集合存在域名与目标冲突，请重新配置后提交"); }
}

