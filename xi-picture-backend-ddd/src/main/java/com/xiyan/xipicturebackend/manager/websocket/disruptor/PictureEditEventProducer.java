package com.xiyan.xipicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.xiyan.xipicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.xiyan.xipicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑事件生产者
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取到可以防止事件的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     * 使用 Disruptor 还有一个优点，可以将任务放到队列中，通过优雅停机机制，在服务停止前执行完所有的任务，再退出服务，防止消息丢失。
     * 为了保证在停机时所有的消息都能够被处理，可以通过 shutdown 方法完成 Disruptor 的优雅停机。
     */
    @PreDestroy
    public void destroy() {
        pictureEditEventDisruptor.shutdown();
    }
}