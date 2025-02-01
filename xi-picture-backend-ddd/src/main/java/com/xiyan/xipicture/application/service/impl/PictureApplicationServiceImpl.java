package com.xiyan.xipicture.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyan.xipicture.domain.picture.service.PictureDomainService;
import com.xiyan.xipicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiyan.xipicture.infrastructure.exception.BusinessException;
import com.xiyan.xipicture.infrastructure.exception.ErrorCode;
import com.xiyan.xipicture.interfaces.dto.picture.*;
import com.xiyan.xipicture.infrastructure.mapper.PictureMapper;
import com.xiyan.xipicture.domain.picture.entity.Picture;
import com.xiyan.xipicture.domain.user.entity.User;
import com.xiyan.xipicture.interfaces.vo.picture.PictureVO;
import com.xiyan.xipicture.interfaces.vo.user.UserVO;
import com.xiyan.xipicture.application.service.PictureApplicationService;
import com.xiyan.xipicture.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xiyan
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2024-12-21 14:46:56
 * 为了方便将 ServiceImpl<PictureMapper, Picture> 看成代码，以便提高一些方法给其他地方使用
 */
@Slf4j
@Service
public class PictureApplicationServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureApplicationService {

    @Resource
    private PictureDomainService pictureDomainService;

    @Resource
    private UserApplicationService userApplicationService;

    /**
     * 校验图片
     *
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        if (picture == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        picture.validPicture();
    }

    /**
     * 上传图片
     *
     * @param inputSource          文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        return pictureDomainService.uploadPicture(inputSource, pictureUploadRequest, loginUser);
    }

    /**
     * 获取图片包装类（单条）
     *
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userApplicationService.getUserById(userId);
            UserVO userVO = userApplicationService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 获取图片包装类（分页）
     *
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        // Set集合是无序的，通过 listByIds 去获取用户列表，那么对应的用户列表就不是之前 pictureList 里面对应的 userId 的顺序，因此就通过 userId 去分组即可
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userApplicationService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 获取查询对象
     *
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        return pictureDomainService.getQueryWrapper(pictureQueryRequest);
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        pictureDomainService.doPictureReview(pictureReviewRequest, loginUser);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        pictureDomainService.fillReviewParams(picture, loginUser);
    }

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        return pictureDomainService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
    }

//    /**
//     * 从缓存获取图片包装类（分页）
//     * Redis 缓存和 Caffeine 本地缓存可以通过 模板方法模式或者策略模式进行修改（两种最重要的区别在于查询和存入操作代码不一样），这样提高代码的复用性
//     * 由于有数据库的操作用模板方法模式可能对该方法不是很好，因此可以抽取出来当中 CacheManager
//     * @param pictureQueryRequest
//     * @param current
//     * @param size
//     * @return
//     */
//    @Override
//    public Page<PictureVO> getPictureVOPageByCache(PictureQueryRequest pictureQueryRequest, long current, long size, HttpServletRequest request) {
//        // 查询缓存，缓存中没有，再查询数据库
//        // 构建缓存的 key
////        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
////        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
////        String cacheKey = String.format("xipicture:listPictureVOByPage:%s", hashKey);
//        String cacheKey = cacheManager.getCacheKey(pictureQueryRequest);
//        // 1. 先从本地缓存中查询
////        Cache<String, String> localCache = caffeineManager.getLocalCache();
////        String cachedValue = localCache.getIfPresent(cacheKey);
////        if (cachedValue != null) {
////            v
////            // 缓存结果比数据库查询的结果大小要小一点，因为这里涉及到转换的过程，这个过程没有将为 null 的数据缓存
////            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
////            return cachedPage;
////        }
//        Page<PictureVO> cachedPage = cacheManager.getCacheDataByCaffeine(cacheKey);
//        if (!cachedPage.getRecords().isEmpty()) {
//            // 如果缓存命中，返回结果
//            return cachedPage;
//        }
//        // 2. 本地缓存未命中，查询 Redis 分布式缓存
////        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
////        cachedValue = opsForValue.get(cacheKey);
////        if (cachedValue != null) {
////            // 如果缓存命中，更新本地缓存，返回结果
////            // 缓存结果比数据库查询的结果大小要小一点，因为这里涉及到转换的过程，这个过程没有将为 null 的数据缓存
////            localCache.put(cacheKey, cachedValue);
////            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
////            return cachedPage;
////        }
//        cachedPage = cacheManager.getCacheDataByRedis(cacheKey);
//        if (!cachedPage.getRecords().isEmpty()) {
//            // 如果缓存命中，更新本地缓存，返回结果
//            cacheManager.updateCaffeineCache(cacheKey, cachedPage);
//            return cachedPage;
//        }
//        // 3. 查询数据库
//        Page<Picture> picturePage = this.page(new Page<>(current, size),
//                this.getQueryWrapper(pictureQueryRequest));
//        // 获取封装类
//        Page<PictureVO> pictureVOPage = this.getPictureVOPage(picturePage, request);
//        // 4. 更新缓存
//        // 4.1 更新 Redis 缓存
//        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
//        // 设置缓存的过期时间，5 - 10 分钟过期，防止缓存雪崩，给过期时间添加一个随机值
//        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
////        opsForValue.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
//        cacheManager.updateRedisCache(cacheKey, pictureVOPage, cacheExpireTime);
//        // 4.2 写入本地缓存
////        localCache.put(cacheKey, cacheValue);
//        cacheManager.updateCaffeineCache(cacheKey, pictureVOPage);
//        return pictureVOPage;
//    }

    /**
     * 清理图片文件
     *
     * @param oldPicture
     */
    @Async  // 可以使得方法被异步调用，记得要在启动类上添加 @EnableAsync 注解才会生效
    @Override
    public void clearPictureFile(Picture oldPicture) {
        pictureDomainService.clearPictureFile(oldPicture);
    }

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        pictureDomainService.deletePicture(pictureId, loginUser);
    }

    /**
     * 删除空间所关联的所有图片
     *
     * @param spaceId
     * @param loginUser
     */
    @Override
    public void deletePicturesBySpaceId(long spaceId, User loginUser) {
        pictureDomainService.deletePicturesBySpaceId(spaceId, loginUser);
    }

    /**
     * 编辑图片
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void editPicture(Picture picture, User loginUser) {
        pictureDomainService.editPicture(picture, loginUser);
    }

    /**
     * 校验空间图片的权限
     *
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        pictureDomainService.checkPictureAuth(loginUser, picture);
    }

    /**
     * 根据颜色搜索图片（现阶段只用于个人空间里）
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        return pictureDomainService.searchPictureByColor(spaceId, picColor, loginUser);
    }

    /**
     * 批量编辑图片（现阶段只用于个人空间里）
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        pictureDomainService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
    }

    /**
     * 创建扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        return pictureDomainService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
    }
}




