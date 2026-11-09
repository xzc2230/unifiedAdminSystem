package org.jeecg.modules.attribution;

import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.modules.attribution.AttributionDomain.ResolveResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttributionResolveController {
  private final AttributionService service;

  public AttributionResolveController(AttributionService service) {
    this.service = service;
  }

  @PostMapping("/api/v1/attribution/resolve")
  public ResponseEntity<ResolveResponse> resolve(HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            service.resolve(
                request.getHeader("Authorization"),
                AttributionHttp.body(request),
                request.getHeader("User-Agent")));
  }
}
