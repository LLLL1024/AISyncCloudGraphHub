package com.xiyan.xipicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xiyan.xipicture.domain.space.entity.SpaceUser;
import com.xiyan.xipicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;

/**
 * @author xiyan
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2025-01-26 11:41:20
 */
public interface SpaceUserDomainService {

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
