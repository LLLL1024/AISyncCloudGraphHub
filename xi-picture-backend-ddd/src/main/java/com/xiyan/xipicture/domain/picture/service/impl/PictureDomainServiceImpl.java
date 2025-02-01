package com.xiyan.xipicture.domain.picture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiyan.xipicture.domain.picture.entity.Picture;
import com.xiyan.xipicture.domain.picture.repository.PictureRepository;
import com.xiyan.xipicture.domain.picture.service.PictureDomainService;
import com.xiyan.xipicture.domain.picture.valueobject.PictureReviewStatusEnum;
import com.xiyan.xipicture.domain.user.entity.User;
import com.xiyan.xipicture.infrastructure.api.CosManager;
import com.xiyan.xipicture.infrastructure.api.aliyunai.AliYunAiApi;
import com.xiyan.xipicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xiyan.xipicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiyan.xipicture.infrastructure.exception.BusinessException;
import com.xiyan.xipicture.infrastructure.exception.ErrorCode;
import com.xiyan.xipicture.infrastructure.exception.ThrowUtils;
import com.xiyan.xipicture.infrastructure.utils.ColorSimilarUtils;
import com.xiyan.xipicture.infrastructure.utils.ColorTransformUtils;
import com.xiyan.xipicture.interfaces.dto.picture.*;
import com.xiyan.xipicture.interfaces.vo.picture.PictureVO;
import com.xiyan.xipicture.infrastructure.manager.upload.FilePictureUpload;
import com.xiyan.xipicture.infrastructure.manager.upload.PictureUploadTemplate;
import com.xiyan.xipicture.infrastructure.manager.upload.UrlPictureUpload;
import com.xiyan.xipicture.infrastructure.manager.upload.model.dto.file.UploadPictureResult;
import com.xiyan.xipicture.domain.space.entity.Space;
import com.xiyan.xipicture.application.service.SpaceApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author xiyan
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2024-12-21 14:46:56
 */
@Slf4j
@Service
public class PictureDomainServiceImpl implements PictureDomainService {
    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private SpaceApplicationService spaceApplicationService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Autowired
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

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
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 改为使用统一的权限校验
            // 校验是否有空间的权限，仅空间管理员（空间用户）才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 判断是新增还是更新
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = pictureRepository.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 改为使用统一的权限校验
            // 仅本人或管理员可编辑图片
            // 这里不能用 ==，因为 Long 是包装类，Integer 也不能，需要用 equals，int 可以 ==。
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅本人或管理员可编辑图片");
//            }
//            boolean exists = this.lambdaQuery()
//                    .eq(Picture::getId, pictureId)
//                    .exists();
//            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId（这样也兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
            // 已经完成：可自行实现，如果是更新，可以清理图片资源。补充更多清理时机：在重新上传图片时，虽然那条图片记录不会删除，但其实之前的图片文件已经作废了，也可以触发清理逻辑。
            this.clearPictureFile(oldPicture);
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录
//        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据 inputSource 的类型区分上传方式
//        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息，也可以用BeanUtils实现（要保证属性名一致）
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 指定空间 id
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setOriginalUrl(uploadPictureResult.getOriginalUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
//        picture.setName(uploadPictureResult.getPicName());
        // 支持外层传递图片名称
        // AI 扩图的 url 通过 getOriginFilename 获取 url 的名字差不多 150，因此 name 字段的长度 128 就不行
        // 方法：修改 name 字段的长度为 256，也可以通过判断修改名字，这里就为了方便就修改一下名字，因为在 AI 扩图后还需要创建，创建时候的名字就是原来的
        String picName = uploadPictureResult.getPicName();
        if (picName.length() > 128) {
            picName = "图片";  // 名称过大就写默认名称（图片）
        }
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
//        picture.setPicColor(uploadPictureResult.getPicColor());
        // 转换为标准颜色
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setUserId(loginUser.getId());
        // 批量设置抓取图片的分类和标签
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getCategory())) {
            picture.setCategory(pictureUploadRequest.getCategory());
        }
        // 标签
        if (pictureUploadRequest != null && CollUtil.isNotEmpty(pictureUploadRequest.getTags())) {
            // 注意将 list 转为 string
            picture.setTags(JSONUtil.toJsonStr(pictureUploadRequest.getTags()));
        }
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
//        // saveOrUpdate 方法会根据是否有 id 来执行 save 还是 update
//        boolean result = this.saveOrUpdate(picture);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = pictureRepository.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间的使用额度
                boolean update = spaceApplicationService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取查询对象
     *
     * @param pictureQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        // 不应该将通过或者拒绝的状态改为带审核 PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = pictureRepository.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 校验审核状态是否重复
        // 不能使用 ==，因为枚举类型是引用类型
//        if (Objects.equals(oldPicture.getReviewStatus(), reviewStatus))
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            // 已经是该状态
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该图片已经审核过了，请勿重复审核");
        }
        // 4. 数据库操作
        // 新建一个对象的好处是只需更新修改的数据字段，用 oldPicture 这样就会将所有有值的字段都更新
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = pictureRepository.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        // todo vip 用户自动过审
//        if (userService.isAdmin(loginUser)) {
//            // 管理员自动过审
//            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
//            picture.setReviewerId(loginUser.getId());
//            picture.setReviewMessage("管理员自动过审");
//            picture.setReviewTime(new Date());
//        } else {
//            // 非管理员，无论是编辑还是创建默认都是待审核
//            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
//        }
        // 已经完成：editPicture 方法过来的没有 spaceId 和 userId，因为 PictureEditRequest 没有这写字段，因此添加即可，不然就会有问题
        boolean isPrivateAndOwned = picture.getSpaceId() != null &&
                picture.getUserId().equals(loginUser.getId());
        // 检查是否为私有空间且属于当前用户，或者用户是管理员
        if (loginUser.isAdmin() || isPrivateAndOwned) {
            // 管理员或私有空间所有者自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage(isPrivateAndOwned ? "私有空间所有者自动过审" : "管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员,或非私有空间所有者的图片需要审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
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
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        String category = pictureUploadByBatchRequest.getCategory();
        List<String> tags = pictureUploadByBatchRequest.getTags();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // aaa.com?name=xx，应该只保留 aaa.com
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            pictureUploadRequest.setCategory(category);
            pictureUploadRequest.setTags(tags);
            try {
                // todo 可以扩展为批量上传
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
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
     * 已经实现了：还要注意删除对象存储中的文件时传入的是 key（不包含域名的相对路径），而数据库中取到的图片地址是包含域名的，所以删除前要移除域名，从而得到 key
     * todo 实现更多清理策略：比如用 Spring Scheduler 定时任务实现定时清理、编写一个接口供管理员手动清理，作为一种兜底策略。
     * todo 优化清理文件的代码，比如要删除多个文件时，使用 对象存储的批量删除接口 代替 for 循环调用。
     * 已经实现了：为了清理原图，可以在数据库中保存原图的地址。
     *
     * @param oldPicture
     */
    @Async  // 可以使得方法被异步调用，记得要在启动类上添加 @EnableAsync 注解才会生效
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断改图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = pictureRepository.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        // 已经实现了：注意，这里的 url 包含了域名，实际上只要传 key 值（存储路径）就够了
        // 提取路径部分
        // 清理压缩图
        cosManager.getUrlCosPathWithDeleteObject(pictureUrl);

        // 清理原始图
        String originalUrl = oldPicture.getOriginalUrl();
        cosManager.getUrlCosPathWithDeleteObject(originalUrl);

        // 删除缩略图，也可以和上面的删除压缩图片一样先判断一下图片是否被多条记录使用，但是该系统没必要
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        cosManager.getUrlCosPathWithDeleteObject(thumbnailUrl);
//        try {
//            // 提取路径部分
//            // 清理压缩图
//            String picturePath = new URL(pictureUrl).getPath();
//            cosManager.deleteObject(picturePath);
//
//            // 清理原始图
//            String originalUrl = oldPicture.getOriginalUrl();
//            if (StrUtil.isNotBlank(originalUrl)) {
//                String originalPath = new URL(originalUrl).getPath();
//                cosManager.deleteObject(originalPath);
//            }
//
//            // 删除缩略图，也可以和上面的删除压缩图片一样先判断一下图片是否被多条记录使用，但是该系统没必要
//            String thumbnailUrl = oldPicture.getThumbnailUrl();
//            if (StrUtil.isNotBlank(thumbnailUrl)) {
//                String thumbnailPath = new URL(thumbnailUrl).getPath();
//                cosManager.deleteObject(thumbnailPath);
//            }
//        } catch (MalformedURLException e) {
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "url 格式错误");
//        }
    }

    /**
     * 删除图片
     * 已经完成：7.4 删除空间时，关联删除空间内的图片，也可以在我的空间加一个删除空间（因为本人也可以）
     * todo 7.5 管理员创建空间：管理员可以为指定用户创建空间。可以在创建空间时多传一个 userId 参数，但是要注意做好权限控制，仅管理员可以为别人创建空间。（目前没啥必要）
     * 已经完成：7.6 目前更新上传图片的逻辑还是存在一些问题的。比如更新图片时，并没有删除原有图片、也没有减少原有图片占用的空间和额度，可以通过事务中补充逻辑或者通过定时任务扫描删除。
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = pictureRepository.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 操作数据库
//        boolean result = pictureService.removeById(id);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR)
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = pictureRepository.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度
            // todo 这里有可能出现对象存储上的图片文件实际没被清理的情况。但是对于用户来说，不应该感受到 “删了图片空间却没有增加”，所以没有将这一步添加到事务中。可以通过定时任务检测作为补偿措施。
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceApplicationService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    /**
     * 删除空间所关联的所有图片
     * todo 11.3 还需要判断是私有空间还是团队空间，如果是团队空间，那还需要删除 SpaceUser
     *
     * @param spaceId
     * @param loginUser
     */
    @Override
    public void deletePicturesBySpaceId(long spaceId, User loginUser) {
        ThrowUtils.throwIf(spaceId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 操作数据库
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id");
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        List<Object> pictureObjList = pictureRepository.getBaseMapper().selectObjs(queryWrapper);
        List<Long> pictureListId = pictureObjList.stream().map(obj -> (Long) obj).collect(Collectors.toList());

        // 清理文件（不能删完 pictureId 后清理文件，不然就找不到了，这里不和上面一样是异步）
        // 这里可以做优化
        for (Long pictureId : pictureListId) {
            Picture picture = pictureRepository.getById(pictureId);
            this.clearPictureFile(picture);
        }

        // 删除完空间后，空间里面的其他数据（比如大小、数量等数据可以不删除，只需删除图片即可，因为那些数据可以方便查看）
        // 其实所有数据都可以用上面的那个 deletePicture 进行删除，现阶段先这样
        boolean result = pictureRepository.removeBatchByIds(pictureListId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 编辑图片
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void editPicture(Picture picture, User loginUser) {
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        picture.validPicture();
        // 判断是否存在
        long id = picture.getId();
        Picture oldPicture = pictureRepository.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureRepository.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 校验空间图片的权限
     *
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !loginUser.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员（空间用户）可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
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
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceApplicationService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = pictureRepository.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 将颜色字符串转换为主色调
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度
                    // 相似度越大越相似，这里 sorted 是升序排序，因此加个负号
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 取前 12 个
                .collect(Collectors.toList());
        // 5. 返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片（现阶段只用于个人空间里）
     * todo 8.5 对于该项目来说，由于用户要处理的数据量不大，下述代码已经能够满足需求。但如果要处理大量数据，可以使用线程池 + 分批 + 并发进行优化.
     * todo 8.5 还可以多记录日志，或者让返回结果更加详细，比如更新成功了多少条数据之类的。
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceApplicationService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = pictureRepository.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 操作数据库进行批量更新
        boolean result = pictureRepository.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    /**
     * 创建扩图任务
     * 已经完成：9.2 扩图的限制：the size of input image is too small or to large，需要修改一下代码
     * * 可以在任务执行前增加基础的校验，只对符合要求的图片创建任务，比如图片不能过大或过小。
     * * 图像限制：
     * * 图像格式：JPG、JPEG、PNG、HEIF、WEBP。
     * * 图像大小：不超过 10MB。
     * * 图像分辨率：不低于 512×512 像素且不超过 4096×4096 像素。
     * * 图像单边长度范围：[512, 4096]，单位像素。
     * 已经完成：9.2 任务错误信息优化：完善任务失败的具体原因，帮助用户快速理解和解决问题。比如参数错误、图片格式不支持等。如果调用了第三方接口，需要认真阅读接口所有可能的错误情况。
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(pictureRepository.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 定义常见的图片后缀
        Set<String> suffixSet = new HashSet<>();
        suffixSet.add("jpg");
        suffixSet.add("jpeg");
        suffixSet.add("png");
        suffixSet.add("heif");
        suffixSet.add("webp");
        // 校验图像格式：JPG、JPEG、PNG、HEIF、WEBP。
        String suffix = FileUtil.getSuffix(picture.getUrl());
        if (!suffixSet.contains(suffix.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持该图片格式");
        }
        // 校验图像大小：不超过 10MB。
        if (picture.getPicSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小超过限制");
        }
        // 校验图像分辨率：不低于 512×512 像素且不超过 4096×4096 像素。
        // 校验图像单边长度范围：[512, 4096]，单位像素。
        if (picture.getPicWidth() < 512 || picture.getPicWidth() > 4096 || picture.getPicHeight() < 512 || picture.getPicHeight() > 4096) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片分辨率不符合要求");
        }
        // 校验权限，已经改为使用注解鉴权
//        checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }
}




