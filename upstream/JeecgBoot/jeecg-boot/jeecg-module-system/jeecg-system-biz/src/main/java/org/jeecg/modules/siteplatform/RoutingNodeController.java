package org.jeecg.modules.siteplatform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Independent node identity. Tenant headers and user sessions never grant node scope. */
@RestController
@RequestMapping("/site-platform/node")
public class RoutingNodeController {
    private final RoutingRepository repository;
    private final String nodeId;
    private final byte[] token;
    private final Map<String,Integer> assignments;
    private final boolean enabled;
    public RoutingNodeController(RoutingRepository repository, @Value("${siteplatform.node.id:}") String id,
        @Value("${siteplatform.node.token:}") String secret, @Value("${siteplatform.node.assignments:}") String scope) {
        this.repository = repository; this.nodeId = id;
        this.token = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        Map<String,Integer> parsed = new TreeMap<>();
        boolean valid = RoutingRules.identifier(id) && token.length >= 32 && token.length <= 512 &&
                secret != null && secret.chars().noneMatch(c -> c <= 32 || c == 127) && scope != null && !scope.isBlank();
        if (valid) {
            String[] entries = scope.split(",", -1);
            valid = entries.length <= 1000;
            for (String entry : entries) {
                String[] pair = entry.trim().split("/", -1);
                if (pair.length != 2 || !pair[0].matches("[1-9][0-9]{0,8}") || !RoutingRules.identifier(pair[1])) { valid = false; break; }
                int tenant = Integer.parseInt(pair[0]);
                Integer previous = parsed.putIfAbsent(pair[1], tenant);
                if (previous != null && previous != tenant) { valid = false; break; }
            }
        }
        enabled = valid && !parsed.isEmpty(); assignments = enabled ? Map.copyOf(parsed) : Map.of();
    }
    private void authenticate(String authorization) {
        if (!enabled) throw new ResourceFailure(404, "接口不存在");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 1024 ||
                !MessageDigest.isEqual(token, authorization.substring(7).getBytes(StandardCharsets.UTF_8))) throw new ResourceFailure(401, "节点凭证无效");
    }
    @ExceptionHandler(ResourceFailure.class)
    public ResponseEntity<Map<String,Object>> failure(ResourceFailure failure) {
        return ResponseEntity.status(failure.status()).body(Map.of("message", failure.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,Object>> malformed() {
        return ResponseEntity.status(enabled ? 400 : 404).body(Map.of("message", enabled ? "请求格式无效" : "接口不存在"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> unexpected(Exception failure) {
        return ResponseEntity.status(enabled ? 503 : 404).body(Map.of("message", enabled ? "节点接口暂不可用" : "接口不存在"));
    }
    @GetMapping(value="/snapshots", produces="application/json")
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> snapshots(@RequestHeader(value="Authorization", required=false) String authorization) {
        authenticate(authorization);
        List<Map<String,Object>> snapshots = new ArrayList<>();
        // One repeatable read transaction makes this a complete, coherent assignment set.
        assignments.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var desired = repository.desired(entry.getValue(), entry.getKey());
            if (desired != null) snapshots.add(RoutingRules.object("payload", desired.get("payload"), "hash", desired.get("hash")));
        });
        return RoutingRules.object("nodeId", nodeId, "snapshots", snapshots);
    }
    @PostMapping(value="/ack", produces="application/json")
    @Transactional
    public Map<String,Object> ack(@RequestHeader(value="Authorization", required=false) String authorization,
                                  @RequestBody(required=false) RoutingRequests.AckInput input) {
        authenticate(authorization);
        if (input == null || !RoutingRules.identifier(input.siteId()) || !RoutingRules.identifier(input.releaseId()) ||
                input.hash() == null || !input.hash().matches("[a-f0-9]{64}")) throw ResourceRules.invalid("确认输入无效");
        Integer tenant = assignments.get(input.siteId());
        if (tenant == null) throw new ResourceFailure(403, "站点不在节点授权范围内");
        if (!repository.lockSite(tenant, input.siteId())) throw new ResourceFailure(409, "期望版本已变更");
        var current = repository.desiredCurrent(tenant, input.siteId());
        if (current == null || !input.releaseId().equals(current.get("id")) || !input.hash().equals(current.get("hash")))
            throw new ResourceFailure(409, "期望版本已变更");
        repository.ack(tenant, input.siteId(), nodeId, input.releaseId(), input.hash());
        return Map.of("accepted", true);
    }
}
