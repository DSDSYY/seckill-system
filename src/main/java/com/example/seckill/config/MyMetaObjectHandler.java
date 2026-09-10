package com.example.seckill.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 *
 * <p>为什么必须有这个类：实体里 createTime/updateTime 标了
 * {@code @TableField(fill = FieldFill.INSERT / INSERT_UPDATE)}，MyBatis-Plus
 * 会在插入/更新时把这两个字段写进 SQL；如果没有本处理器给它们赋值，
 * 写入的就是 null，数据库列又是 NOT NULL，于是报错
 * "Column 'create_time' cannot be null"（订单落库失败、消息进死信队列）。
 *
 * <p>注册为 Spring Bean 后，MP 自动调用 insertFill / updateFill 填充当前时间。
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // strict 版本：仅当字段存在且当前为 null 时才填充，避免覆盖业务显式赋值
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}