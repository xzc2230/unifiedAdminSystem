package org.jeecg.modules.packagemanagement.job;
public class PackageStoreJob implements org.quartz.Job {
 private final org.jeecg.modules.packagemanagement.application.store.IStoreStatusService service;
 public PackageStoreJob(){this.service=null;}
 public PackageStoreJob(org.jeecg.modules.packagemanagement.application.store.IStoreStatusService service){this.service=service;}
 public void ryGetStatus(){(service!=null?service:org.jeecg.common.util.SpringContextUtils.getBean(org.jeecg.modules.packagemanagement.application.store.IStoreStatusService.class)).getStatus();}
 @Override public void execute(org.quartz.JobExecutionContext context)throws org.quartz.JobExecutionException{
  try{ryGetStatus();}catch(RuntimeException failure){throw new org.quartz.JobExecutionException("Package store status check failed",failure,false);}
 }
}
