package org.jeecg.modules.attribution;

import java.util.*;

public class AttributionFailure extends RuntimeException {
  public final int status;
  public final String code;
  public final List<Map<String, String>> fieldErrors;

  public AttributionFailure(int status, String message) {
    this(status, message, List.of());
  }

  public AttributionFailure(int status, String message, List<Map<String, String>> errors) {
    super(message);
    this.status = status;
    this.code =
        switch (status) {
          case 400 -> "A0400";
          case 401 -> "A0300";
          case 403 -> "A0301";
          case 404 -> "A0404";
          case 409 -> "A0409";
          case 429 -> "A0501";
          case 503 -> "B0210";
          default -> "B0001";
        };
    this.fieldErrors = errors;
  }

  public static AttributionFailure field(String path, String message) {
    return new AttributionFailure(400, message, List.of(Map.of("path", path, "message", message)));
  }
}
