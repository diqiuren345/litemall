package org.linlinjava.litemall.admin.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.authz.annotation.*;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/index")
public class AdminIndexController {
    private static final String MSG_INDEX_HELLO = "hello world, this is admin service";

    private final Log logger = LogFactory.getLog(AdminIndexController.class);

    @RequestMapping("/index")
    public Object index() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresGuest
    @RequestMapping("/guest")
    public Object guest() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresAuthentication
    @RequestMapping("/authn")
    public Object authn() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresUser
    @RequestMapping("/user")
    public Object user() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresRoles("admin")
    @RequestMapping("/admin")
    public Object admin() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresRoles("admin2")
    @RequestMapping("/admin2")
    public Object admin2() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresPermissions("index:permission:read")
    @RequiresPermissionsDesc(menu = {"其他", "权限测试"}, button = "权限读")
    @GetMapping("/read")
    public Object read() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

    @RequiresPermissions("index:permission:write")
    @RequiresPermissionsDesc(menu = {"其他", "权限测试"}, button = "权限写")
    @PostMapping("/write")
    public Object write() {
        return ResponseUtil.ok(MSG_INDEX_HELLO);
    }

}
