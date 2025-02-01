package com.xiyan.xipicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyan.xipicture.domain.user.entity.User;
import com.xiyan.xipicture.domain.user.repository.UserRepository;
import com.xiyan.xipicture.infrastructure.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户仓储实现
 */
@Service
public class UserRepositoryImpl extends ServiceImpl<UserMapper, User> implements UserRepository {
}