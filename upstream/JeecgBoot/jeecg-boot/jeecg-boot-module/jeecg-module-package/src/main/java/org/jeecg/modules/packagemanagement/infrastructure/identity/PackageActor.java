package org.jeecg.modules.packagemanagement.infrastructure.identity;
/** Trusted application identity; never bind this type from request data. */
public record PackageActor(int tenantId,String userId,long legacyUserId,boolean administrator)  {
}
