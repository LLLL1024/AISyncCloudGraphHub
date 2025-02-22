package com.xiyan.xipicturebackend.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * VIP 角色（只是扩展了，但是还没有校验 vip，对于 admin 来说，开通会员不需要降级成 vip）
     */
    String VIP_ROLE = "vip";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    // endregion
}