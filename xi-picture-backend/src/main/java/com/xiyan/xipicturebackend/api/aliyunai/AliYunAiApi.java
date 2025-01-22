package com.xiyan.xipicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.xiyan.xipicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.xiyan.xipicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.xiyan.xipicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.xiyan.xipicturebackend.exception.BusinessException;
import com.xiyan.xipicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阿里云 AI 接口
 * https://help.aliyun.com/zh/model-studio/developer-reference/image-scaling-api?spm=a2c4g.11186623.0.0.7b8368bbS7RZJ4
 * todo 9.2 扩图的限制：the size of input image is too small or to large，需要修改一下代码
     * 可以在任务执行前增加基础的校验，只对符合要求的图片创建任务，比如图片不能过大或过小。
     * 图像限制：
     * 图像格式：JPG、JPEG、PNG、HEIF、WEBP。
     * 图像大小：不超过 10MB。
     * 图像分辨率：不低于 512×512 像素且不超过 4096×4096 像素。
     * 图像单边长度范围：[512, 4096]，单位像素。
 * todo 9.2 任务记录和状态管理：现在用户是无法找到往期执行的任务和生成的图片的。
     * 可以设计任务记录表，存储每个任务的状态、结果和相关信息，并提供接口供用户查询历史任务。
     * 前端可以给用户提供往期任务查询页面，能够查看任务结果、重试某一次任务等。还可以给管理员提供监控系统所有任务的页面，比如任务数、成功率和失败率，全面掌握任务执行情况。
 * todo 9.2 任务错误信息优化：完善任务失败的具体原因，帮助用户快速理解和解决问题。比如参数错误、图片格式不支持等。如果调用了第三方接口，需要认真阅读接口所有可能的错误情况。
 * todo 9.2 计费与额度控制：AI 扩图一般是计费业务，需要做好额度控制，并且仅登录用户才可以使用。
     * 在用户表中添加“扩图额度”（比如使用次数或余额），每次提交任务前先检查额度是否足够，额度不足则提示用户充值。
     * 每次任务提交时，可采用预扣费逻辑，任务完成扣费，任务失败则自动退还额度。
     * 提供查询用户当前剩余额度的接口，用户可以在前端看到自己剩余的额度。
     * 支持充值额度或会员订阅制收费，还可以根据扩图模式按比例扣费。比如普通模式扣 1 点，高清模式扣 2 点。
 * todo 9.2 尝试更多 AI 图片处理能力
 * todo 9.2 如果 AI 绘画 API 支持返回当前进度（比如 MidJourney 的 API），可以通过 SSE 的方式将进度返回给前端.
 * todo 9.2 优化 AI 扩图参数，补充更多扩图参数，并允许用户自主选择扩图参数。
 */
@Slf4j
@Component
public class AliYunAiApi {

    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 创建任务地址
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";  // %s 为 {taskId}

    /**
     * 创建任务
     *
     * @param createOutPaintingTaskRequest
     * @return
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        // 发送请求
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                // 注意，要按照官方文档的要求给请求头增加鉴权信息，拼接配置中写好的 apiKey
                .header("Authorization", "Bearer " + apiKey)
                // 必须开启异步处理，异步是必填的（enable），为什么这里还要填？方便后面的扩展和显示传递，同步就是将 enable 改为 false
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        // 处理响应
        // 自动关闭资源（httpResponse）， Java 7 引入的一种新的异常处理机制（try-with-resources），用于简化资源管理。它能够自动关闭在 try 语句块中使用的资源，从而避免资源泄露问题。
        // 注意，这里使用 try-with-resources，是因为 HttpResponse 是实现了 AutoCloseable 接口的
        // 使用 try-with-resource，那么对象必须要实现 Closeable 里面的 AutoCloseable
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            // code（string） 接口错误码。接口成功请求不会返回该参数。
            if (createOutPaintingTaskResponse.getCode() != null) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常：{}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败，" + errorMessage);
            }
            return createOutPaintingTaskResponse;
        }
    }

    /**
     * 查询创建的任务结果
     *
     * @param taskId
     * @return
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 ID 不能为空");
        }
        // 处理响应
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = HttpRequest.get(url)
                // 注意，要按照官方文档的要求给请求头增加鉴权信息，拼接配置中写好的 apiKey
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}