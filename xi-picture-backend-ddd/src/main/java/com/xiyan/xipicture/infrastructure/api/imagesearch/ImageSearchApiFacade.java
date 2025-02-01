package com.xiyan.xipicture.infrastructure.api.imagesearch;

import cn.hutool.core.io.FileUtil;
import com.xiyan.xipicture.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.xiyan.xipicture.infrastructure.api.imagesearch.sub.GetImageFirstUrlApi;
import com.xiyan.xipicture.infrastructure.api.imagesearch.sub.GetImageListApi;
import com.xiyan.xipicture.infrastructure.api.imagesearch.sub.GetImagePageUrlApi;
import com.xiyan.xipicture.infrastructure.exception.BusinessException;
import com.xiyan.xipicture.infrastructure.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 图片搜索门面类
 * 运用一种设计模式来提供图片搜索服务。门面模式通过提供一个统一的接口来简化多个接口的调用，使得客户端不需要关注内部的具体实现。
 * 可以将多个 API 整合到一个门面类中，简化调用过程。
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        // 判断只能接收 png 和 jpg 的图片
        // 将后缀统一转化为小写
        String suffix = FileUtil.getSuffix(imageUrl);
        suffix = suffix.toLowerCase();
        if (!suffix.equals("png") && !suffix.equals("jpg")) {
            log.error("图片格式不正确，只能接收 png 和 jpg 的图片");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片格式不正确，只能接收 png 和 jpg 的图片");
        }
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        List<ImageSearchResult> imageList = searchImage("https://www.codefather.cn/logo.png");
        System.out.println("结果列表" + imageList);
    }
}