package com.xiyan.xipicture.infrastructure.manager.upload;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.xiyan.xipicture.infrastructure.exception.BusinessException;
import com.xiyan.xipicture.infrastructure.exception.ErrorCode;
import com.xiyan.xipicture.infrastructure.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL 图片上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 1. 校验非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");
        // 2. 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        // 3. 校验 URL 的协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址"
        );
        // 4. 发送 HEAD 请求验证文件是否存在
        HttpResponse httpResponse = null;
        try {
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .execute();
            // 未正常返回，无需执行其他判断
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 5. 文件存在，文件类型校验
            String contentType = httpResponse.header("Content-Type");
            // 不为空，才校验是否合法，这样校验规则相对宽松
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 6. 文件存在，文件大小校验
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024 * 1024;
                    ThrowUtils.throwIf(contentLength > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }
            }
        } finally {
            // 记得释放资源
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 批量抓取的 url 后面本身可能没有文件名后缀（如 png），导致保存到数据库的 url 后面没有文件名后缀（如 png），因此下载图片的图片无法显示
        // 方法一（只解决了 url 后面有后缀的）
        //从 url 中提取原始文件名 比如: https://xxxx.cn/logo.png --> logo.png
        // 测试发现 url 里面没有后缀，那么自己在 url 后面拼接后缀 .png（"jpeg", "png", "jpg", "webp"）
        URL url = null;
        try {
            url = new URL(fileUrl);
            // 这个方法调用会从路径部分中截取从最后一个斜杠到字符串末尾的部分。FileUtil.getSuffix 用来获取文件名的后缀（即文件扩展名）的
            fileUrl = url.getFile().substring(url.getFile().lastIndexOf('/'));  // /logo.png
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "url 格式错误");
        }
        return fileUrl;
        // 方法二（只解决了 url 后面有后缀的）
//        return fileUrl.substring(fileUrl.lastIndexOf('/') + 1);  // 获取文件名，带后缀，logo.png
        // 该返回的问题：url 上传图片在数据库中不显示图片格式后缀
        // 解决办法：修改获取原始文件名方法
//        return FileUtil.mainName(fileUrl);  // 获取文件名，不会带后缀（如 png），logo
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(fileUrl, file);
    }
}