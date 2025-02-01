package com.xiyan.xipicture.shared.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片编辑请求消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditRequestMessage {

    /**
     * 消息类型，例如 "ENTER_EDIT", "EXIT_EDIT", "EDIT_ACTION"
     * "INFO", "ERROR" 是服务器返回给前端的一个消息提示，但是前端不会主动给后端发 "INFO", "ERROR" 的事件
     */
    private String type;

    /**
     * 执行的编辑动作（放大、缩小等）
     */
    private String editAction;
}