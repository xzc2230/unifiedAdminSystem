package org.jeecg.modules.packagemanagement.job;

/** App Store listing check. Only applications whose platform is ios. */
public class PackageIosStoreJob extends PackageStoreJob {
    public PackageIosStoreJob() {}

    public PackageIosStoreJob(org.jeecg.modules.packagemanagement.application.store.IStoreStatusService service) {
        super(service);
    }

    @Override
    public void ryGetStatus() {
        probe("ios");
    }
}
