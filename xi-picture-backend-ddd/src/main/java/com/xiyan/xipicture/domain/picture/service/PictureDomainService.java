package com.xiyan.xipicture.domain.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xiyan.xipicture.domain.picture.entity.Picture;
import com.xiyan.xipicture.domain.user.entity.User;
import com.xiyan.xipicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiyan.xipicture.interfaces.dto.picture.*;
import com.xiyan.xipicture.interfaces.vo.picture.PictureVO;

import java.util.List;

/**
 * @author xiyan
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2024-12-21 14:46:56
 */
public interface PictureDomainService {

    /**
     * 上传图片
     *
     * @param inputSource 文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 获取查询对象
     *
     * @param pictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                 User loginUser);

//    /**
//     * 从缓存获取图片包装类（分页）
//     *
//     * @param pictureQueryRequest
//     * @param current
//     * @param size
//     * @return
//     */
//    Page<PictureVO> getPictureVOPageByCache(PictureQueryRequest pictureQueryRequest, long current, long size, HttpServletRequest request);

    /**
     * 清理图片文件
     *
     * @param oldPicture
     */
    void clearPictureFile(Picture oldPicture);

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 删除空间所关联的所有图片
     *
     * @param spaceId
     * @param loginUser
     */
    void deletePicturesBySpaceId(long spaceId, User loginUser);

    /**
     * 编辑图片
     *
     * @param picture
     * @param loginUser
     */
    void editPicture(Picture picture, User loginUser);

    /**
     * 校验空间图片的权限
     *
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 根据颜色搜索图片（现阶段只用于个人空间里）
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 批量编辑图片（现阶段只用于个人空间里）
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 创建扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);
}
