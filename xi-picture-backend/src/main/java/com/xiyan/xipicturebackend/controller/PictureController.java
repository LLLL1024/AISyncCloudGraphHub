package com.xiyan.xipicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xiyan.xipicturebackend.annotation.AuthCheck;
import com.xiyan.xipicturebackend.api.imagesearch.ImageSearchApiFacade;
import com.xiyan.xipicturebackend.api.imagesearch.model.ImageSearchResult;
import com.xiyan.xipicturebackend.common.BaseResponse;
import com.xiyan.xipicturebackend.common.DeleteRequest;
import com.xiyan.xipicturebackend.common.ResultUtils;
import com.xiyan.xipicturebackend.constant.UserConstant;
import com.xiyan.xipicturebackend.exception.BusinessException;
import com.xiyan.xipicturebackend.exception.ErrorCode;
import com.xiyan.xipicturebackend.exception.ThrowUtils;
import com.xiyan.xipicturebackend.model.dto.picture.*;
import com.xiyan.xipicturebackend.model.entity.Picture;
import com.xiyan.xipicturebackend.model.entity.Space;
import com.xiyan.xipicturebackend.model.entity.User;
import com.xiyan.xipicturebackend.model.enums.PictureReviewStatusEnum;
import com.xiyan.xipicturebackend.model.vo.PictureTagCategory;
import com.xiyan.xipicturebackend.model.vo.PictureVO;
import com.xiyan.xipicturebackend.service.PictureService;
import com.xiyan.xipicturebackend.service.SpaceService;
import com.xiyan.xipicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {
    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    /**
//     * 本地缓存
//     * 单独封装为一个类
//     */
//    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
//            .initialCapacity(1024)
//            .maximumSize(10_000L) // 最大 10000 条
//            // 缓存 5 分钟后移除
//            .expireAfterWrite(Duration.ofMinutes(5))
//            .build();

    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 删除图片
     * 本人也可以删除自己的图片（仅本人或者管理员可删除）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest
            , HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
//        Long id = deleteRequest.getId();
//        // 判断是否存在
//        Picture oldPicture = pictureService.getById(id);
//        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
//        // 仅本人或者管理员可删除
//        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//        }
//        // 操作数据库
//        boolean result = pictureService.removeById(id);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
//        // 清理图片资源
//        pictureService.clearPictureFile(oldPicture);
        pictureService.deletePicture(deleteRequest.getId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
//        pictureService.fillReviewParams(oldPicture, loginUser);
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
        }
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
//        // 普通用户默认只能看到审核通过的数据
//        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（封装类，有缓存）
     * todo 图片优化中的查询优化里面还有几个扩展的地方，如 提高一个手动刷新缓存的接口（可以先查数据库在更新缓存） 和 使用 HotKey 自动识别热点图片缓存
     */
    @Deprecated  // 用户的私有空间不需要缓存，因此可以去掉，该接口当学习用
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 在里面的代码可以写到 Service 层，因为 Service 层是业务逻辑层，而 Controller 层是控制层，不应该有业务逻辑
//        // 查询缓存，缓存中没有，再查询数据库
//        // 构建缓存的 key
//        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
//        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
//        String cacheKey = String.format("xipicture:listPictureVOByPage:%s", hashKey);
//        // 1. 先从本地缓存中查询
//        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
//        if (cachedValue!= null) {
//            // 如果缓存命中，返回结果
//            // 缓存结果比数据库查询的结果大小要小一点，因为这里涉及到转换的过程，这个过程没有将为 null 的数据缓存
//            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
//            return ResultUtils.success(cachedPage);
//        }
//        // 2. 本地缓存未命中，查询 Redis 分布式缓存
//        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
//        cachedValue = opsForValue.get(cacheKey);
//        if (cachedValue != null) {
//            // 如果缓存命中，更新本地缓存，返回结果
//            // 缓存结果比数据库查询的结果大小要小一点，因为这里涉及到转换的过程，这个过程没有将为 null 的数据缓存
//            LOCAL_CACHE.put(cacheKey, cachedValue);
//            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
//            return ResultUtils.success(cachedPage);
//        }
//        // 3. 查询数据库
//        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
//                pictureService.getQueryWrapper(pictureQueryRequest));
//        // 获取封装类
//        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
//        // 4. 更新缓存
//        // 4.1 更新 Redis 缓存
//        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
//        // 设置缓存的过期时间，5 - 10 分钟过期，防止缓存雪崩，给过期时间添加一个随机值
//        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
//        opsForValue.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
//        // 4.2 写入本地缓存
//        LOCAL_CACHE.put(cacheKey, cacheValue);

        Page<PictureVO> pictureVOPageByCache = pictureService.getPictureVOPageByCache(pictureQueryRequest, current, size, request);
        // 返回结果
        return ResultUtils.success(pictureVOPageByCache);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
//        // 在此处将实体类和 DTO 进行转换
//        Picture picture = new Picture();
//        BeanUtils.copyProperties(pictureEditRequest, picture);
//        // 注意将 list 转为 string
//        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
//        // 设置编辑时间，editTime要用户编辑的时候才会更新，所以应该要用一个用户编辑的功能来更新editTime
//        picture.setEditTime(new Date());
//        // 数据校验
//        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
////        // 补充审核参数
////        pictureService.fillReviewParams(picture, loginUser);
//        // 判断是否存在
//        long id = pictureEditRequest.getId();
//        Picture oldPicture = pictureService.getById(id);
//        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
//        // 仅本人或管理员可编辑
//        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//        }
//        // 补充审核参数
//        pictureService.fillReviewParams(picture, loginUser);
//        // 操作数据库
//        boolean result = pictureService.updateById(picture);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 图片审核
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取并创建图片
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 以图搜图
     * todo 8.2 解决 webp 格式图片无法搜索的问题，该以图搜图只能接收 png 和 jpg 的图片（解决办法可以给用户提示只能接收 png 和 jpg，不然就报错）
     *  如果想解决上述问题，有几种方案：
     *      1. 直接在前端拿到识图结果 URL 后，直接新页面打开，而不是把识图结果放到自己的网站页面中
     *      2. 切换为其他识图接口，比如 Bing 以图搜图 API（可以换成 Bing 的）
     *      3. 将本项目的图片以 PNG 格式进行压缩
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
//        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(picture.getUrl());
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(picture.getOriginalUrl());
        return ResultUtils.success(resultList);
    }

    /**
     * 按照颜色搜索
     */
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOList);
    }
}