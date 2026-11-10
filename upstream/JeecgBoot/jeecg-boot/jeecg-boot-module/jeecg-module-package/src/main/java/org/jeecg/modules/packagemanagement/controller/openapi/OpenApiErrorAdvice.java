package org.jeecg.modules.packagemanagement.controller.openapi;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
/** Preserve the original public AjaxResult error envelope for binding and runtime failures. */
@RestControllerAdvice(assignableTypes=OpenApiManagerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenApiErrorAdvice {
 @ExceptionHandler(Exception.class) public String failure(Exception failure){return OpenApiManagerController.json(OpenApiManagerController.error(500,failure.getMessage()));}
}
