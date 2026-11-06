package org.jeecg.modules.siteplatform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Explicit write allowlists; ownership and verification fields are never bound. */
public final class ResourceRequests {
    private ResourceRequests() {}
    public record SiteInput(@NotBlank @Size(max=100) String name, @NotBlank String environment, @NotNull Boolean enabled) {}
    public record TargetInput(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=2000) String url, @NotNull Boolean enabled) {}
    public record DomainInput(@NotBlank @Size(max=253) String hostname) {}
    public record MemberInput(@NotBlank @Size(max=32) String userId, @NotBlank String role) {}
}
