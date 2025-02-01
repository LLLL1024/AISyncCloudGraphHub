package com.xiyan.xipicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyan.xipicture.domain.space.entity.Space;
import com.xiyan.xipicture.domain.space.repository.SpaceRepository;
import com.xiyan.xipicture.infrastructure.mapper.SpaceMapper;
import org.springframework.stereotype.Service;

/**
 * 空间仓储实现
 */
@Service
public class SpaceRepositoryImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceRepository {
}