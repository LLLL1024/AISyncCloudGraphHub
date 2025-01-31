package com.xiyan.xipicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.xiyan.xipicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.xiyan.xipicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.xiyan.xipicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.xiyan.xipicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.xiyan.xipicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.xiyan.xipicturebackend.model.entity.User;
import com.xiyan.xipicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑 WebSocket 处理器
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    @Lazy  // 防止循环依赖
    private PictureEditEventProducer pictureEditEventProducer;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID，保证线程安全要用线程安全的并发哈希 Map
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合，保证线程安全要用线程安全的并发哈希 Map
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 连接建立成功
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);
        // 构造响应，发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 收到前端发送的消息，根据消息类别处理消息
     *
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 将消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);

        // 从 Session 属性中获取公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // 根据消息类型处理消息（生产消息到 Disruptor 环形队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);

//        // 调用对应的消息处理方法（收到前端发送的消息，根据消息类别处理消息）
//        switch (pictureEditMessageTypeEnum) {
//            // "INFO", "ERROR" 是服务器返回给前端的一个消息提示，但是前端不会主动给后端发 "INFO", "ERROR" 的事件，因此不需要这两种
//            // todo 12.2 这三个方法的参数是一致的，因此可以使用策略模式去实现（每种类型的消息处理可以封装为独立的 Handler 处理器类，也就是采用策略模式。）
//            case ENTER_EDIT:
//                handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
//                break;
//            case EDIT_ACTION:
//                handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
//                break;
//            case EXIT_EDIT:
//                handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
//                break;
//            default:
//                // 其他消息类型，返回错误提示给当前的用户（前端）
//                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
//                pictureEditResponseMessage.setMessage("消息类型错误");
//                pictureEditResponseMessage.setUser(userService.getUserVO(user));
//                // 按理来说这里也要处理 JSON 精度问题，但这里只是一个 ERROR，所以就不处理了
//                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
//                break;
//        }
    }


    /**
     * 进入编辑状态
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置用户正在编辑该图片
            pictureEditingUsers.put(pictureId, user.getId());
            // 构造响应，发送加入编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("用户 %s 开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给所有用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 处理编辑操作
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.error("无效的编辑动作");
            return;
        }
        // 确认是当前的编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 构造响应，发送具体操作的通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s 执行 %s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 退出编辑状态
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 确认是当前的编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除用户正在编辑该图片
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("用户 %s 退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 关闭连接
     *
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);
        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        // sessionSet != null 有个点就是空集合也会加入该判断，因为哪怕是空集合也会有个 pictureId，所以要将 pictureId 完整的 key 删除
        if (sessionSet != null) {
            sessionSet.remove(session);
            // 所有用户都离开会话（直到最后一个用户离开），那么就要删除 pictureId 这个 key
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }
        // 通知其他用户，该用户已经离开编辑
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 广播给该图片的所有用户（支持排除掉某个 Session（比如：当前在编辑图片的用户））
     *
     * @param pictureId
     * @param pictureEditResponseMessage
     * @param excludeSession
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws IOException {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && session.equals(excludeSession)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 广播给该图片的所有用户
     *
     * @param pictureId
     * @param pictureEditResponseMessage
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    // todo 12.1 为防止消息丢失，可以使用 Redis 等高性能存储保存执行的操作记录。
    // 12.1 目前如果图片已经被编辑了，新用户加入编辑时没办法查看到已编辑的状态，这一点也可以利用 Redis 保存操作记录来解决，新用户加入编辑时读取 Redis 的操作记录即可。
    // todo 12.3 支持分布式 WebSocket。实现思路很简单，只需要保证要编辑同一图片的用户连接的是相同的服务器即可，和游戏分服务器大区、聊天室分房间是类似的原理。
    // todo 12.4 一些小问题的优化：比如 WebSocket 连接建立之后，如果用户退出了登录，这时 WebSocket 的连接是没有断开的。不过影响并不大，大家可以思考下怎么处理。（目前可以不处理）
}