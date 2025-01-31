package com.xiyan.xipicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新空间请求（是给管理员用的，所以需要编辑 spaceLevel）
 */
@Data
public class SpaceUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小（用户可以买扩容包进行扩容）
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    private static final long serialVersionUID = 1L;
}