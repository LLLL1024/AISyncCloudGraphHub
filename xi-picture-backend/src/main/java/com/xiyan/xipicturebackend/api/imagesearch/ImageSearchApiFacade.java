package com.xiyan.xipicturebackend.api.imagesearch;

import com.xiyan.xipicturebackend.api.imagesearch.model.ImageSearchResult;
import com.xiyan.xipicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.xiyan.xipicturebackend.api.imagesearch.sub.GetImageListApi;
import com.xiyan.xipicturebackend.api.imagesearch.sub.GetImagePageUrlApi;
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