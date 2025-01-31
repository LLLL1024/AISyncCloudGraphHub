package com.xiyan.xipicturebackend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用的删除请求类
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 通常出现在实现了Serializable接口的类中
     * 在Java中，当一个对象需要被序列化（即转换为字节流以便存储或传输）时，serialVersionUID 用于确保序列化和反序列化过程中类的兼容性。
     * 如果类的定义在序列化和反序列化之间发生了变化，而serialVersionUID 保持不变，那么Java虚拟机（JVM）仍然可以正确地反序列化对象。
     * 如果没有显式地定义serialVersionUID，JVM会根据类的细节（如字段、方法等）自动生成一个。但是，自动生成的serialVersionUID 在类结构变化时可能会发生变化，导致反序列化失败。
     * 因此，为了确保类的兼容性，通常建议显式地定义serialVersionUID。
     */
    private static final long serialVersionUID = 1L;
}
