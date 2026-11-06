package org.jeecg.modules.siteplatform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Only explicitly editable fields are accepted; identity and approval are server owned. */
public final class RoutingRequests {
    private RoutingRequests() {}
    public record DraftInput(@Min(0) long revision, @NotBlank @Size(max=32) String defaultTargetId,
                             @NotEmpty @Size(max=100) List<@NotBlank @Size(max=32) String> domainIds,
                             @NotNull @Size(max=100) List<@NotNull @Valid RuleInput> rules) {}
    public record RuleInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,32}") String id,
                            @Size(max=32) String domainId, @NotBlank @Size(max=2048) String path,
                            @NotBlank String matchType, @NotNull Integer priority,
                            @NotBlank @Size(max=32) String targetId) {}
    public record RevisionInput(@Min(0) long revision) {}
    public record SimulateInput(@NotBlank String hostname, @NotBlank @Size(max=2048) String path,
                                @NotNull @Valid DraftInput input) {}
    public record AckInput(@NotBlank @Size(max=32) String siteId, @NotBlank @Size(max=32) String releaseId,
                           @NotBlank @Pattern(regexp="[0-9a-f]{64}") String hash) {}
}

