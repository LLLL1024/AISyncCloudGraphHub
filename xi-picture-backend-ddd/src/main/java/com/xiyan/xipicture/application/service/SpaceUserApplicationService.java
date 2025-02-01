package com.xiyan.xipicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiyan.xipicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.xiyan.xipicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.xiyan.xipicture.domain.space.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiyan.xipicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author xiyan
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-01-26 11:41:20
*/
public interface SpaceUserApplicationService extends IService<SpaceUser> {

    // 不需要获取 loginUser，因为要用 Sa-Token 权限校验框架

    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add       是否为创建时检验
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员包装类（列表，不用分页，查询所有）
     *
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
