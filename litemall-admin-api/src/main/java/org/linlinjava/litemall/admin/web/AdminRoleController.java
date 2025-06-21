package org.linlinjava.litemall.admin.web;

//import io.swagger.models.auth.In;
//import io.swagger.models.
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.apache.shiro.SecurityUtils;
//import org.apache.shiro.authz.annotation.RequiresPermissions;
//import org.apache.shiro.subject.Subject;
import org.linlinjava.litemall.admin.annotation.RequiresPermissionsDesc;
import org.linlinjava.litemall.admin.util.AdminResponseCode;
import org.linlinjava.litemall.admin.util.Permission;
import org.linlinjava.litemall.admin.util.PermissionUtil;
import org.linlinjava.litemall.admin.vo.PermVo;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallPermission;
import org.linlinjava.litemall.db.domain.LitemallRole;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallPermissionService;
import org.linlinjava.litemall.db.service.LitemallRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.security.Security;
import java.util.*;

import static org.linlinjava.litemall.admin.util.AdminResponseCode.*;

@RestController
@RequestMapping("/admin/role")
@Validated
//Added info for adapting authentication with swagger3
@Tag(name = "Admin Role Management", description = "Apis for management admin roles")
public class AdminRoleController {
    private final Log logger = LogFactory.getLog(AdminRoleController.class);

    @Autowired
    private LitemallRoleService roleService;
    @Autowired
    private LitemallPermissionService permissionService;
    @Autowired
    private LitemallAdminService adminService;

    //@RequiresPermissions("admin:role:list")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "角色查询")
    //@RequiresPermissionsDesc(menu = {"System management", "role management"}, button = "role query")
    @GetMapping("/list")
    //@Operation(summary = "list roles", description = "Get a list roles with pagination and sorting" )
    @SecurityRequirement(name = "apiKey")
    public Object list(String name,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallRole> roleList = roleService.querySelective(name, page, limit, sort, order);
        return ResponseUtil.okList(roleList);
    }

    @GetMapping("/options")
    public Object options() {
        List<LitemallRole> roleList = roleService.queryAll();

        List<Map<String, Object>> options = new ArrayList<>(roleList.size());
        for (LitemallRole role : roleList) {
            Map<String, Object> option = new HashMap<>(2);
            option.put("value", role.getId());
            option.put("label", role.getName());
            options.add(option);
        }

        return ResponseUtil.okList(options);
    }

    //@RequiresPermissions("admin:role:read")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "角色详情")
    @GetMapping("/read")
    //@Operation(summary = "read roles", description = "Get a list roles with pagination and sorting" )
    @SecurityRequirement(name = "apiKey")
    public Object read(@NotNull Integer id) {
        LitemallRole role = roleService.findById(id);
        return ResponseUtil.ok(role);
    }


    private Object validate(LitemallRole role) {
        String name = role.getName();
        if (StringUtils.isEmpty(name)) {
            return ResponseUtil.badArgument();
        }

        return null;
    }

    //@RequiresPermissions("admin:role:create")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "角色添加")
    @PostMapping("/create")
    public Object create(@RequestBody LitemallRole role) {
        Object error = validate(role);
        if (error != null) {
            return error;
        }

        if (roleService.checkExist(role.getName())) {
            return ResponseUtil.fail(ROLE_NAME_EXIST, "The role already exists");
        }

        roleService.add(role);

        return ResponseUtil.ok(role);
    }

    //@RequiresPermissions("admin:role:update")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "角色编辑")
    @PostMapping("/update")
    public Object update(@RequestBody LitemallRole role) {
        Object error = validate(role);
        if (error != null) {
            return error;
        }

        roleService.updateById(role);
        return ResponseUtil.ok();
    }

    //@RequiresPermissions("admin:role:delete")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "角色删除")
    @PostMapping("/delete")
    public Object delete(@RequestBody LitemallRole role) {
        Integer id = role.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }

        // 如果当前角色所对应管理员仍存在，则拒绝删除角色。
        List<LitemallAdmin> adminList = adminService.all();
        for (LitemallAdmin admin : adminList) {
            Integer[] roleIds = admin.getRoleIds();
            for (Integer roleId : roleIds) {
                if (id.equals(roleId)) {
                    return ResponseUtil.fail(ROLE_USER_EXIST, "There is an administrator in the current role，cannot be deleted");
                }
            }
        }

        roleService.deleteById(id);
        return ResponseUtil.ok();
    }


    @Autowired
    private ApplicationContext context;
    private List<PermVo> systemPermissions = null;
    private Set<String> systemPermissionsString = null;

    private List<PermVo> getSystemPermissions() {
        final String basicPackage = "org.linlinjava.litemall.admin";
        if (systemPermissions == null) {
            List<Permission> permissions = PermissionUtil.listPermission(context, basicPackage);
            systemPermissions = PermissionUtil.listPermVo(permissions);
            systemPermissionsString = PermissionUtil.listPermissionString(permissions);
        }
        return systemPermissions;
    }

    private Set<String> getAssignedPermissions(List<Integer> roleIds) {
        // What needs to be noted here is that，If super permissions exist，Then this needs to be converted into all current system permissions。
        // The reason for doing this，It's because the front end cannot recognize super permissions，So you need to convert it here。
        Set<String> assignedPermissions = null;
        if (permissionService.checkSuperPermission(roleIds)) {
            getSystemPermissions();
            assignedPermissions = systemPermissionsString;
        } else {
            assignedPermissions = permissionService.queryByRoleId(roleIds);
        }

        return assignedPermissions;
    }

    /**
     * Administrator's permissions
     *
     * @return List of all system permissions、Role permissions、Administrator has assigned permissions
     */
    //@RequiresPermissions("admin:role:permission:get")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "权限详情")
    @GetMapping("/permissions")
    public Object getPermissions(Integer roleId) {
        List<PermVo> systemPermissions = getSystemPermissions();

        // What needs to be noted here is that，If super permissions exist，Then this needs to be converted into all current system permissions
        // The reason for doing this，It's because the front end cannot recognize super permissions，So you need to convert it here
        Set<String> assignedPermissions = null;
        if (permissionService.checkSuperPermission(roleId)) {
            getSystemPermissions();
            assignedPermissions = systemPermissionsString;
        } else {
            assignedPermissions = permissionService.queryByRoleId(roleId);
        }

       /* Subject currentUser = SecurityUtils.getSubject();
        LitemallAdmin currentAdmin = (LitemallAdmin) currentUser.getPrincipal();
        Integer[] roles = currentAdmin.getRoleIds();
        List<Integer> roleIds = Arrays.asList(roles);
        Set<String> curPermissions = null;*/
        /*if (!permissionService.checkSuperPermission(roleIds)) {
            curPermissions = permissionService.queryByRoleId(roleIds);
        }*/


       /* Map<String, Object> data = new HashMap<>();
        data.put("systemPermissions", systemPermissions);
        data.put("assignedPermissions", assignedPermissions);
        data.put("curPermissions", curPermissions);
        return ResponseUtil.ok(data); */
        return ResponseUtil.ok();
    }


    /**
     * 更新管理员的权限
     *
     * @param body
     * @return
     */
    //@RequiresPermissions("admin:role:permission:update")
    @RequiresPermissionsDesc(menu = {"系统管理", "角色管理"}, button = "权限变更")
    @PostMapping("/permissions")
    public Object updatePermissions(@RequestBody String body) {
        Integer roleId = JacksonUtil.parseInteger(body, "roleId");
        List<String> permissions = JacksonUtil.parseStringList(body, "permissions");
        if (roleId == null || permissions == null) {
            return ResponseUtil.badArgument();
        }

        // If the modified role has super permissions，then reject the modification。
        if (permissionService.checkSuperPermission(roleId)) {
            return ResponseUtil.fail(AdminResponseCode.ROLE_SUPER_SUPERMISSION, "The super permissions of the current role cannot be changed.");
        }

        // Delete old permissions first，Update new permissions
        permissionService.deleteByRoleId(roleId);
        for (String permission : permissions) {
            LitemallPermission litemallPermission = new LitemallPermission();
            litemallPermission.setRoleId(roleId);
            litemallPermission.setPermission(permission);
            permissionService.add(litemallPermission);
        }
        return ResponseUtil.ok();
    }

}
