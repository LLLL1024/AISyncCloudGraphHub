package com.xiyan.xipicturebackend.manager.websocket.model;

import com.xiyan.xipicture.interfaces.vo.user.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片编辑响应消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditResponseMessage {

    /**
     * 消息类型，例如 "INFO", "ERROR", "ENTER_EDIT", "EXIT_EDIT", "EDIT_ACTION"
     */
    private String type;

    /**
     * 信息
     */
    private String message;

    /**
     * 执行的编辑动作（放大、缩小等）
     */
    private String editAction;

    /**
     * 用户信息（当前哪个用户在编辑）
     */
    private UserVO user;
}