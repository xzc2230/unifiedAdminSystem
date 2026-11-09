package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.jeecg.modules.packagemanagement.job.PackageStoreJob;
import org.jeecg.modules.packagemanagement.application.store.IStoreStatusService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageJobTest {
 @Test void quartzExecutesTheMigratedStoreCheck()throws Exception{
  IStoreStatusService service=mock(IStoreStatusService.class);Object job=new PackageStoreJob(service);
  assertInstanceOf(Job.class,job);((Job)job).execute(null);verify(service).getStatus();
  assertInstanceOf(Job.class,PackageStoreJob.class.getDeclaredConstructor().newInstance());
 }
}
