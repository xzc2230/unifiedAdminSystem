package org.jeecg.modules.attribution;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Locale;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.attribution.AttributionDomain.Actor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

/**
 * HTTP input is bounded before JSON parsing. Identity comes from the authenticated host subject.
 */
final class AttributionHttp {
  static final int MAX_BODY_BYTES = 65536;
  private static final ObjectMapper JSON =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  static {
    JSON.getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxNestingDepth(12)
                .maxStringLength(8192)
                .maxNumberLength(32)
                .build());
  }

  private AttributionHttp() {}

  static JsonNode body(HttpServletRequest request) {
    String type = request.getContentType();
    if (type == null
        || !type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals("application/json")) {
      throw new AttributionFailure(400, "Content-Type must be application/json");
    }
    if (request.getContentLengthLong() > MAX_BODY_BYTES)
      throw new AttributionFailure(400, "Request body is too large");
    try {
      byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
      if (bytes.length > MAX_BODY_BYTES)
        throw new AttributionFailure(400, "Request body is too large");
      try (var parser = JSON.getFactory().createParser(bytes)) {
        JsonNode value = JSON.readTree(parser);
        if (value == null || !value.isObject() || parser.nextToken() != null) {
          throw new AttributionFailure(400, "Expected one JSON object");
        }
        return value;
      }
    } catch (IOException invalid) {
      throw new AttributionFailure(400, "Invalid JSON request");
    }
  }

  static Actor actor(
      HttpServletRequest request,
      String permission,
      AttributionRepository repository,
      AttributionProperties properties) {
    var subject = SecurityUtils.getSubject();
    if (!subject.isAuthenticated() || !(subject.getPrincipal() instanceof LoginUser user)) {
      throw new AttributionFailure(401, "Host authentication required");
    }
    if (!subject.isPermitted(permission)) throw new AttributionFailure(403, "Permission denied");
    Actor actor =
        new Actor(
            request.getHeader(org.jeecg.common.constant.CommonConstant.TENANT_ID), user.getId());
    repository.requireMember(actor);
    repository.rate(
        "admin:" + actor.scopeId() + ":" + actor.userId(), properties.getAdminRequestsPerMinute());
    return actor;
  }

  static ResponseEntity<Result<Object>> ok(Object data) {
    // The Boot 4 host uses Jackson 3: do not pass Jackson 2 tree nodes to its serializer.
    Object wire = data instanceof JsonNode node ? JSON.convertValue(node, Object.class) : data;
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(wire));
  }
}
