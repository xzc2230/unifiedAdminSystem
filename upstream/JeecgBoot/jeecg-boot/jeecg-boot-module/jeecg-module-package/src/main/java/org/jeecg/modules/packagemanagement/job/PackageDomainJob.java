package org.jeecg.modules.packagemanagement.job;

import org.jeecg.modules.packagemanagement.application.domains.IClientDomainService;

public class PackageDomainJob implements org.quartz.Job {
    private final IClientDomainService service;

    public PackageDomainJob() { this.service = null; }

    public PackageDomainJob(IClientDomainService service) { this.service = service; }

    @Override
    public void execute(org.quartz.JobExecutionContext context) throws org.quartz.JobExecutionException {
        try {
            IClientDomainService target = service != null ? service : org.jeecg.common.util.SpringContextUtils.getBean(IClientDomainService.class);
            target.syncPending();
        } catch (RuntimeException failure) {
            throw new org.quartz.JobExecutionException("Package domain check failed", failure, false);
        }
    }
}
