package org.jeecg.modules.attribution;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.attribution.AttributionDomain.Actor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/attribution-admin")
public class AttributionAdminController {
  private final AttributionService service;
  private final AttributionRepository repository;
  private final AttributionProperties properties;

  public AttributionAdminController(
      AttributionService service,
      AttributionRepository repository,
      AttributionProperties properties) {
    this.service = service;
    this.repository = repository;
    this.properties = properties;
  }

  private Actor actor(HttpServletRequest request, String permission) {
    return AttributionHttp.actor(request, "attribution:" + permission, repository, properties);
  }

  @GetMapping("/capabilities")
  public ResponseEntity<Result<Object>> capabilities(HttpServletRequest request) {
    actor(request, "application:list");
    return AttributionHttp.ok(Map.of("countryEnabled", false));
  }

  @GetMapping("/applications")
  public ResponseEntity<Result<Object>> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpServletRequest request) {
    return AttributionHttp.ok(
        service.list(actor(request, "application:list"), q, pageNo, pageSize));
  }

  @GetMapping("/application-configurations/{app}/channels/{channel}")
  public ResponseEntity<Result<Object>> configuration(
      @PathVariable String app, @PathVariable String channel, HttpServletRequest request) {
    return AttributionHttp.ok(
        service.configuration(actor(request, "application:list"), app, channel));
  }

  @GetMapping("/applications/{app}/channels/{channel}/offers")
  public ResponseEntity<Result<Object>> offers(
      @PathVariable String app,
      @PathVariable String channel,
      @RequestParam String configVersion,
      HttpServletRequest request) {
    return AttributionHttp.ok(
        service.offers(actor(request, "application:list"), app, channel, configVersion));
  }

  @PostMapping("/application-configurations")
  public ResponseEntity<Result<Object>> create(HttpServletRequest request) {
    Actor actor = actor(request, "application:add");
    return AttributionHttp.ok(
        service.save(
            actor,
            null,
            null,
            request.getHeader("Idempotency-Key"),
            AttributionHttp.body(request)));
  }

  @PutMapping("/application-configurations/{app}/channels/{channel}")
  public ResponseEntity<Result<Object>> update(
      @PathVariable String app, @PathVariable String channel, HttpServletRequest request) {
    Actor actor = actor(request, "application:edit");
    return AttributionHttp.ok(
        service.save(
            actor,
            app,
            channel,
            request.getHeader("Idempotency-Key"),
            AttributionHttp.body(request)));
  }

  @PostMapping("/applications/{app}/subjects")
  public ResponseEntity<Result<Object>> subject(
      @PathVariable String app, HttpServletRequest request) {
    Actor actor = actor(request, "integration:manage");
    return AttributionHttp.ok(service.issueSubject(actor, app, AttributionHttp.body(request)));
  }

  @PostMapping("/applications/{app}/channels/{channel}/clicks")
  public ResponseEntity<Result<Object>> click(
      @PathVariable String app, @PathVariable String channel, HttpServletRequest request) {
    Actor actor = actor(request, "integration:manage");
    return AttributionHttp.ok(
        service.click(
            actor, app, channel, AttributionHttp.body(request), request.getHeader("User-Agent")));
  }

  @PostMapping("/applications/{app}/clicks/{click}/evidence")
  public ResponseEntity<Result<Object>> evidence(
      @PathVariable String app, @PathVariable String click, HttpServletRequest request) {
    Actor actor = actor(request, "integration:manage");
    service.evidence(actor, app, click, AttributionHttp.body(request));
    return AttributionHttp.ok(Map.of("accepted", true));
  }

  @PostMapping("/applications/{app}/safety")
  public ResponseEntity<Result<Object>> safety(
      @PathVariable String app, HttpServletRequest request) {
    Actor actor = actor(request, "security:manage");
    service.safety(actor, app, AttributionHttp.body(request));
    return AttributionHttp.ok(Map.of("saved", true));
  }

  @PostMapping("/applications/{app}/grants")
  public ResponseEntity<Result<Object>> grant(
      @PathVariable String app, HttpServletRequest request) {
    Actor actor = actor(request, "integration:manage");
    service.grant(actor, app, AttributionHttp.body(request));
    return AttributionHttp.ok(Map.of("saved", true));
  }

  @GetMapping("/applications/{app}/clicks/{click}/diagnostics")
  public ResponseEntity<Result<Object>> diagnostics(
      @PathVariable String app, @PathVariable String click, HttpServletRequest request) {
    return AttributionHttp.ok(service.diagnostics(actor(request, "diagnostics:view"), app, click));
  }
}
