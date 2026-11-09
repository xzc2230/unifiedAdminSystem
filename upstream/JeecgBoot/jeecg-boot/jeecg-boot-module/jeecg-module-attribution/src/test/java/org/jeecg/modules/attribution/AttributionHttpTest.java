package org.jeecg.modules.attribution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.attribution.AttributionDomain.Actor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AttributionHttpTest {
  @AfterEach
  void clearSubject() {
    ThreadContext.unbindSubject();
  }

  MockHttpServletRequest request(String text) {
    var request = new MockHttpServletRequest();
    request.setContentType("application/json; charset=UTF-8");
    request.setContent(text.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  @Test
  void boundedJsonRejectsDuplicateKeysTrailingObjectsAndDeepNesting() {
    assertEquals(
        "value", AttributionHttp.body(request("{\"key\":\"value\"}")).path("key").asText());
    for (String input :
        new String[] {
          "{\"key\":1,\"key\":2}",
          "{} {}",
          "[]",
          "",
          "{\"x\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}"
        }) {
      assertEquals(
          400,
          assertThrows(AttributionFailure.class, () -> AttributionHttp.body(request(input)))
              .status);
    }
  }

  @Test
  void knownAndChunkedOversizedBodiesAreRejectedBeforeParsing() {
    var known = request(" ".repeat(AttributionHttp.MAX_BODY_BYTES + 1));
    assertEquals(
        400, assertThrows(AttributionFailure.class, () -> AttributionHttp.body(known)).status);
    var chunked = spy(request(" ".repeat(AttributionHttp.MAX_BODY_BYTES + 1)));
    doReturn(-1L).when(chunked).getContentLengthLong();
    assertEquals(
        400, assertThrows(AttributionFailure.class, () -> AttributionHttp.body(chunked)).status);
    var wrongType = request("{}");
    wrongType.setContentType("text/plain");
    assertEquals(
        400, assertThrows(AttributionFailure.class, () -> AttributionHttp.body(wrongType)).status);
  }

  @Test
  void actorUsesAuthenticatedPrincipalAndVerifiesTenantMembership() {
    Subject subject = mock(Subject.class);
    ThreadContext.bind(subject);
    var repository = mock(AttributionRepository.class);
    var properties = new AttributionProperties();
    var request = request("{}");
    request.addHeader("X-Tenant-Id", "1000");
    request.addHeader("tenant-id", "999999");
    assertEquals(
        401,
        assertThrows(
                AttributionFailure.class,
                () ->
                    AttributionHttp.actor(
                        request, "attribution:application:list", repository, properties))
            .status);
    var user = new LoginUser();
    user.setId("verified-user");
    when(subject.isAuthenticated()).thenReturn(true);
    when(subject.getPrincipal()).thenReturn(user);
    assertEquals(
        403,
        assertThrows(
                AttributionFailure.class,
                () ->
                    AttributionHttp.actor(
                        request, "attribution:application:list", repository, properties))
            .status);
    when(subject.isPermitted("attribution:application:list")).thenReturn(true);
    Actor actor =
        AttributionHttp.actor(request, "attribution:application:list", repository, properties);
    assertEquals(new Actor("1000", "verified-user"), actor);
    verify(repository).requireMember(actor);
    verify(repository).rate("admin:1000:verified-user", properties.getAdminRequestsPerMinute());
    doThrow(new AttributionFailure(403, "Active tenant membership required"))
        .when(repository)
        .requireMember(actor);
    assertEquals(
        403,
        assertThrows(
                AttributionFailure.class,
                () ->
                    AttributionHttp.actor(
                        request, "attribution:application:list", repository, properties))
            .status);
  }

  @Test
  void configurationTreesCrossTheHostJsonVersionBoundaryAsPlainValues() {
    var input = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
    input.put("expectedConfigVersion", "9007199254740993");
    input.putArray("offers");
    Object output = AttributionHttp.ok(input).getBody().getResult();
    assertInstanceOf(Map.class, output);
    assertEquals("9007199254740993", ((Map<?, ?>) output).get("expectedConfigVersion"));
    assertInstanceOf(java.util.List.class, ((Map<?, ?>) output).get("offers"));
  }

  @Test
  void errorsUseHostEnvelopeOnlyForManagementAndNeverExposeExceptionDetails() {
    var advice = new AttributionHttpAdvice();
    var admin = request("{}");
    admin.setServletPath("/attribution-admin/application-configurations");
    var invalid = AttributionFailure.field("offers[0].weight", "Invalid integer");
    var response = advice.failure(invalid, admin);
    assertEquals(400, response.getStatusCode().value());
    assertEquals("no-store", response.getHeaders().getCacheControl());
    var body = (Map<?, ?>) response.getBody();
    assertEquals(false, body.get("success"));
    assertEquals(400, body.get("code"));
    assertTrue(body.containsKey("result"));
    assertEquals(
        400,
        advice
            .failure(
                new org.springframework.web.bind.MissingServletRequestParameterException(
                    "configVersion", "String"),
                admin)
            .getStatusCode()
            .value());
    var external = request("{}");
    external.setServletPath("/api/v1/attribution/resolve");
    var unavailable = advice.failure(new IllegalStateException("sensitive-token-secret"), external);
    assertEquals(503, unavailable.getStatusCode().value());
    assertFalse(unavailable.getBody().toString().contains("sensitive-token-secret"));
    assertFalse(((Map<?, ?>) unavailable.getBody()).containsKey("result"));
    assertEquals(
        "60",
        advice
            .failure(new AttributionFailure(429, "limit"), external)
            .getHeaders()
            .getFirst("Retry-After"));
  }
}
