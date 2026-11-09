package org.jeecg.modules.attribution;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Deliberately excludes request bodies, credentials and database exception messages. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = {AttributionAdminController.class, AttributionResolveController.class})
public class AttributionHttpAdvice {
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> failure(Exception error, HttpServletRequest request) {
    AttributionFailure failure =
        error instanceof AttributionFailure known
            ? known
            : error instanceof org.springframework.web.bind.ServletRequestBindingException
                    || error
                        instanceof
                        org.springframework.web.method.annotation
                            .MethodArgumentTypeMismatchException
                ? new AttributionFailure(400, "Invalid request parameter")
                : new AttributionFailure(503, "Attribution service temporarily unavailable");
    boolean admin = request.getServletPath().startsWith("/attribution-admin");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", admin ? failure.status : failure.code);
    body.put("message", failure.getMessage());
    body.put("traceId", UUID.randomUUID().toString());
    if (admin) {
      body.put("success", false);
      body.put("result", Map.of("fieldErrors", failure.fieldErrors));
    }
    var response = ResponseEntity.status(failure.status).cacheControl(CacheControl.noStore());
    if (failure.status == 429) response.header("Retry-After", "60");
    return response.body(body);
  }
}
