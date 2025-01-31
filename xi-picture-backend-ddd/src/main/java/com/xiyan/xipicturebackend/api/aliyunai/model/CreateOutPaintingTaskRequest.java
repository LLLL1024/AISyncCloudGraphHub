package com.xiyan.xipicturebackend.api.aliyunai.model;


import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建扩图任务请求
 */
@Data
public class CreateOutPaintingTaskRequest implements Serializable {

    /**
     * 模型，例如 "image-out-painting"
     */
    private String model = "image-out-painting";

    /**
     * 输入图像信息
     */
    private Input input;

    /**
     * 图像处理参数
     */
    private Parameters parameters;

    @Data
    public static class Input {

        /**
         * 必选，图像 URL
         */
        // @Alias 注解会告诉 Lombok 在处理 imageUrl 字段时使用 image_url 作为别名。
        // 某些字段打上了 Hutool 工具类的 @Alias 注解，这个注解仅对 Hutool 的 JSON 转换生效，对 SpringMVC 的 JSON 转换没有任何影响。
        @Alias("image_url")
        private String imageUrl;
    }

    @Data
    public static class Parameters implements Serializable {

        /**
         * 可选，逆时针旋转角度，默认值 0，取值范围 [0, 359]
         */
        private Integer angle;

        /**
         * 可选，输出图像的宽高比，默认空字符串，不设置宽高比
         * 可选值：["", "1:1", "3:4", "4:3", "9:16", "16:9"]
         */
        @Alias("output_ratio")
        private String outputRatio;

        /**
         * 可选，图像居中，在水平方向上按比例扩展，默认值 1.0，范围 [1.0, 3.0]
         */
        // 经过测试发现，前端如果传递参数名 xScale，是无法赋值给 xScale 字段的；但是传递参数名 xscale，就可以赋值。
        // 这是因为 SpringMVC 对于第二个字母是大写的参数无法映射（和参数类别无关）
        // 解决方案是，给这些字段增加 @JsonProperty 注解
        // 当 Jackson 库在处理 Java 对象和 JSON 之间的转换时，它会查找类中的字段和方法的注解来决定如何进行映射。@JsonProperty 注解告诉 Jackson，在 JSON 数据中，这个字段应该被映射为指定的 JSON 属性名。
        @Alias("x_scale")
        @JsonProperty("xScale")
        private Float xScale;

        /**
         * 可选，图像居中，在垂直方向上按比例扩展，默认值 1.0，范围 [1.0, 3.0]
         */
        @Alias("y_scale")
        @JsonProperty("yScale")
        private Float yScale;

        /**
         * 可选，在图像上方添加像素，默认值 0
         */
        @Alias("top_offset")
        private Integer topOffset;

        /**
         * 可选，在图像下方添加像素，默认值 0
         */
        @Alias("bottom_offset")
        private Integer bottomOffset;

        /**
         * 可选，在图像左侧添加像素，默认值 0
         */
        @Alias("left_offset")
        private Integer leftOffset;

        /**
         * 可选，在图像右侧添加像素，默认值 0
         */
        @Alias("right_offset")
        private Integer rightOffset;

        /**
         * 可选，开启图像最佳质量模式，默认值 false
         * 若为 true，耗时会成倍增加
         */
        @Alias("best_quality")
        private Boolean bestQuality;

        /**
         * 可选，限制模型生成的图像文件大小，默认值 true
         * - 单边长度 <= 10000：输出图像文件大小限制为 5MB 以下
         * - 单边长度 > 10000：输出图像文件大小限制为 10MB 以下
         */
        @Alias("limit_image_size")
        private Boolean limitImageSize;

        /**
         * 可选，添加 "Generated by AI" 水印，默认值 true
         */
        @Alias("add_watermark")
        private Boolean addWatermark = false;
    }
}