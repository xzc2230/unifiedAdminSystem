package org.jeecg.modules.packagemanagement.controller.admin;

import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.home.PackageHomeCounts;
import org.jeecg.modules.packagemanagement.application.home.PackageHomeService;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Homepage store counts for any signed-in member of the current tenant. */
@RestController
@RequestMapping("/package-admin/home")
public class PackageHomeController {
    private final PackageHomeService service;
    private final PackageAccess access;

    public PackageHomeController(PackageHomeService service, PackageAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/summary")
    public Result<PackageHomeCounts.Report> summary() {
        return Result.ok(service.summary(access.current()));
    }
}
