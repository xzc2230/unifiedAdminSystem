package org.jeecg.modules.attribution;

import java.util.*;

/** Immutable values; persisted IDs and versions remain strings at every HTTP boundary. */
public final class AttributionDomain {
  private AttributionDomain() {}

  public record Actor(String scopeId, String userId) {}

  public record Condition(String field, String operator, List<String> values) {
    public Condition {
      values = List.copyOf(values);
    }
  }

  public record Offer(
      String offerId, String targetUrl, Set<String> platforms, Long validFrom, Long validTo) {
    public Offer {
      platforms = Set.copyOf(platforms);
    }
  }

  public record Binding(
      String clientKey,
      String bindingId,
      String channelId,
      String bindingScope,
      int weight,
      String matchMode,
      List<Condition> conditions,
      Offer offer,
      int sortOrder) {
    public Binding {
      conditions = List.copyOf(conditions);
    }
  }

  public record Channel(
      String channelId, String channelCode, String channelName, String mediaType) {}

  public record Config(
      String applicationId,
      String applicationCode,
      String applicationName,
      String currentChannelId,
      String fallbackUrl,
      String configVersion,
      List<Channel> channels,
      List<Binding> bindings) {
    public Config {
      channels = List.copyOf(channels);
      bindings = List.copyOf(bindings);
    }
  }

  public record Click(
      String scopeId,
      String applicationId,
      String channelId,
      String clickRef,
      String entryKey,
      String subjectId,
      String configVersion,
      String bindingId,
      Offer offer,
      String originalUrl,
      List<String> candidates,
      Map<String, String> afLinkParams,
      Map<String, String> fbLinkParams,
      String os) {}

  public record Subject(String scopeId, String applicationId, String subjectId) {}

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
  public record ResolveResponse(
      String clickRef,
      String resolveToken,
      String applicationCode,
      String promotionCode,
      String resolutionState,
      String parameterSource,
      String destinationUrl,
      Map<String, String> afLinkParams,
      Map<String, String> fbLinkParams) {}
}
