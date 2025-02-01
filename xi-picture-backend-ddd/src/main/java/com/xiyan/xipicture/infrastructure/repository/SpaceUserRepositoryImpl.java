package com.xiyan.xipicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyan.xipicture.domain.space.entity.SpaceUser;
import com.xiyan.xipicture.domain.space.repository.SpaceUserRepository;
import com.xiyan.xipicture.infrastructure.mapper.SpaceUserMapper;
import org.springframework.stereotype.Service;

/**
 * 空间用户仓储实现
 */
@Service
public class SpaceUserRepositoryImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserRepository {
}