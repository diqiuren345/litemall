package org.linlinjava.litemall.admin.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.admin.vo.PermVo;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.*;

public class PermissionUtil {

    public static List<PermVo> listPermVo(List<Permission> permissions) {
        List<PermVo> root = new ArrayList<>();
        for (Permission permission : permissions) {
            RequiresPermissions requiresPermissions = permission.getRequiresPermissions();
            RequiresPermissionsDesc desc = permission.getRequiresPermissionsDesc();

            String[] menus = desc.menu();
            if (menus.length != 2) {
                throw new IllegalStateException("目前只支持两级菜单");
            }

            PermVo menuPerm = findOrCreateByLabel(root, menus[0]);
            PermVo subMenuPerm = findOrCreateByLabel(menuPerm.getChildren(), menus[1]);

            String button = desc.button();
            if (findByLabel(subMenuPerm.getChildren(), button) != null) {
                // 目前限制 Controller 每个方法的 RequiresPermissionsDesc 唯一，
                // 重复说明权限定义冲突，内部权限可能不一致
                throw new IllegalStateException("权限已经存在，不能添加新权限");
            }
            PermVo buttonPerm = new PermVo();
            buttonPerm.setId(requiresPermissions.value()[0]);
            buttonPerm.setLabel(button);
            buttonPerm.setApi(permission.getApi());
            subMenuPerm.getChildren().add(buttonPerm);
        }
        return root;
    }

    private static PermVo findByLabel(List<PermVo> perms, String label) {
        for (PermVo perm : perms) {
            if (perm.getLabel().equals(label)) {
                return perm;
            }
        }
        return null;
    }

    private static PermVo findOrCreateByLabel(List<PermVo> perms, String label) {
        PermVo perm = findByLabel(perms, label);
        if (perm == null) {
            perm = new PermVo();
            perm.setId(label);
            perm.setLabel(label);
            perm.setChildren(new ArrayList<>());
            perms.add(perm);
        }
        return perm;
    }

    public static List<Permission> listPermission(ApplicationContext context, String basicPackage) {
        Map<String, Object> map = context.getBeansWithAnnotation(Controller.class);
        List<Permission> permissions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object bean = entry.getValue();
            if (!StringUtils.contains(ClassUtils.getPackageName(bean.getClass()), basicPackage)) {
                continue;
            }

            Class<?> clz = bean.getClass();
            Class controllerClz = clz.getSuperclass();
            RequestMapping clazzRequestMapping = AnnotationUtils.findAnnotation(controllerClz, RequestMapping.class);
            List<Method> methods = MethodUtils.getMethodsListWithAnnotation(controllerClz, RequiresPermissions.class);
            for (Method method : methods) {
                RequiresPermissions requiresPermissions = AnnotationUtils.getAnnotation(method,
                        RequiresPermissions.class);
                RequiresPermissionsDesc requiresPermissionsDesc = AnnotationUtils.getAnnotation(method,
                        RequiresPermissionsDesc.class);

                if (requiresPermissions == null || requiresPermissionsDesc == null) {
                    continue;
                }

                String api = "";
                if (clazzRequestMapping != null) {
                    api = clazzRequestMapping.value()[0];
                }

                permissions.add(buildPermission(method, requiresPermissions, requiresPermissionsDesc, api));
            }
        }
        return permissions;
    }

    private static Permission buildPermission(Method method, RequiresPermissions requiresPermissions,
                                              RequiresPermissionsDesc desc, String baseApi) {
        PostMapping postMapping = AnnotationUtils.getAnnotation(method, PostMapping.class);
        String api;
        if (postMapping != null) {
            api = "POST " + baseApi + postMapping.value()[0];
        } else {
            GetMapping getMapping = AnnotationUtils.getAnnotation(method, GetMapping.class);
            if (getMapping == null) {
                // 这里只支持 GetMapping 或 PostMapping，应该进一步提供灵活性
                throw new IllegalStateException("目前权限管理应该在method的前面使用GetMapping注解或者PostMapping注解");
            }
            api = "GET " + baseApi + getMapping.value()[0];
        }
        Permission permission = new Permission();
        permission.setRequiresPermissions(requiresPermissions);
        permission.setRequiresPermissionsDesc(desc);
        permission.setApi(api);
        return permission;
    }

    public static Set<String> listPermissionString(List<Permission> permissions) {
        Set<String> permissionsString = new HashSet<>();
        for (Permission permission : permissions) {
            permissionsString.add(permission.getRequiresPermissions().value()[0]);
        }
        return permissionsString;
    }
}
