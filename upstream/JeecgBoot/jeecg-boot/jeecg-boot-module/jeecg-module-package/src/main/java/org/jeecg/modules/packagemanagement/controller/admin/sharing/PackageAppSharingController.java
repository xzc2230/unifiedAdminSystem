package org.jeecg.modules.packagemanagement.controller.admin.sharing;

import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.sharing.PackageAppSharingService;
import org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-admin/apps")
public class PackageAppSharingController {
    private final PackageAdminSupport guard;
    private final PackageAppSharingService sharing;

    public PackageAppSharingController(PackageAdminSupport guard, PackageAppSharingService sharing) {
        this.guard = guard;
        this.sharing = sharing;
    }

    public record ShareRequest(List<Long> ids, String targetUser, String reason) {}

    @PostMapping("/shares")
    public Result<Integer> grant(@RequestBody ShareRequest request) {
        var actor = guard.actor("apps", "transfer");
        if (request == null) throw new PackageFailure(400, "共享请求不能为空");
        return Result.ok(sharing.grant(actor, request.ids(), request.targetUser(), request.reason()));
    }

    @PostMapping("/shares/revoke")
    public Result<Integer> revoke(@RequestBody ShareRequest request) {
        var actor = guard.actor("apps", "transfer");
        if (request == null) throw new PackageFailure(400, "共享请求不能为空");
        return Result.ok(sharing.revoke(actor, request.ids(), request.targetUser(), request.reason()));
    }

    @GetMapping("/{id}/shares")
    public Result<Map<String,Object>> shares(@PathVariable("id") long id) {
        return Result.ok(sharing.shares(guard.actor("apps", "transfer"), id));
    }

    @GetMapping("/{id}/share-audit")
    public Result<Map<String,Object>> audit(@PathVariable("id") long id) {
        return Result.ok(sharing.audit(guard.actor("apps", "transfer"), id));
    }
}
