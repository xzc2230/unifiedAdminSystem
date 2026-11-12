package org.jeecg.modules.packagemanagement.job;

/** Google Play listing check. Only applications whose platform is android. */
public class PackageAndroidStoreJob extends PackageStoreJob {
    public PackageAndroidStoreJob() {}

    public PackageAndroidStoreJob(org.jeecg.modules.packagemanagement.application.store.IStoreStatusService service) {
        super(service);
    }

    @Override
    public void ryGetStatus() {
        probe("android");
    }
}
