package org.jeecg.modules.packagemanagement.controller.admin;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Advice is ordered before the host catch-all, but applies only to package management controllers.
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="org.jeecg.modules.packagemanagement.controller.admin")
public class PackageExceptionHandler {
    @ExceptionHandler(PackageFailure.class) public ResponseEntity<Result<Object>> failure(PackageFailure failure) {
        return ResponseEntity.status(failure.getCode()).body(Result.error(failure.getCode(),failure.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.beans.BeansException.class})
    public ResponseEntity<Result<Object>> invalid(Exception failure) {
        return ResponseEntity.badRequest().body(Result.error(400,"请求字段或格式无效"));
    }
}
