package com.xiyan.xipicture.infrastructure.utils;

/**
 * 颜色转换工具类
 */
public class ColorTransformUtils {

    private ColorTransformUtils() {
        // 工具类不需要实例化
    }

    /**
     * 获取标准颜色（将数据万象的 4 / 5 位色值转为 6 位）
     * todo 8.3 rgb 可能不正确需要修改一下代码
     * todo 8.3 刷新历史数据，让所有的图片都有主色调。
     * todo 8.3 将颜色搜索和其他的搜索相结合，比如先用其他的搜索条件过滤数据，再运用相似度算法排序。
     * todo 8.3 将颜色搜索应用到主页公共图库、图片管理页面等。
     * todo 8.3 使用 ES 分词搜索图片的名称和简介.
     * todo 8.3 自动按日期将图片分类到不同的文件夹中。
     * todo 8.3 颜色检索时，定义一个阈值范围，过滤掉不相似颜色。
     *
     * @param color
     * @return
     */
    public static String getStandardColor(String color) {
        // 每一种 rgb 色值都有可能只有一个 0，要转换为 00)
        // 如果是六位，不用转换，如果是五位，要给第三位后面加个 0
        // 示例：
        // 0x080e0 => 0x0800e
        // 0xbe11 => 0xbe1100
        if (color.length() == 6) {
            color = color + "00";
        } else if (color.length() == 7) {
            color = color.substring(0, 4) + "0" + color.substring(4, 7);
        }
        return color;
    }
}