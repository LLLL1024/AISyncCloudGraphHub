package com.xiyan.xipicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyan.xipicture.domain.picture.entity.Picture;
import com.xiyan.xipicture.domain.picture.repository.PictureRepository;
import com.xiyan.xipicture.infrastructure.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
 * 图片仓储实现
 */
@Service
public class PictureRepositoryImpl extends ServiceImpl<PictureMapper, Picture> implements PictureRepository {
}