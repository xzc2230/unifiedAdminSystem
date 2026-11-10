package org.jeecg.modules.packagemanagement.job;
/** Register this class through JEECG Quartz only when package schema and mail configuration are ready. */
@org.quartz.DisallowConcurrentExecution
public class PackagePackStatJob implements org.quartz.Job {
 @Override public void execute(org.quartz.JobExecutionContext context)throws org.quartz.JobExecutionException{
  try{org.jeecg.common.util.SpringContextUtils.getBean(org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatDetectService.class).runRound();}
  catch(RuntimeException e){throw new org.quartz.JobExecutionException("Package statistics detection failed",e,false);}
 }
}
