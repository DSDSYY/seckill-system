package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体 —— 对应表 product（普通商品基础资料）
 *
 * <p>为什么单独拆一张商品表：
 * <ol>
 *   <li>商品是"稳定资料"，秒杀是"营销玩法"，一个商品可以参加多轮秒杀活动；
 *   <li>活动表只冗余 product_id，商品改名/改价不会牵连历史活动与订单；
 *   <li>未来做普通下单/购物车时，商品表可以直接复用。
 * </ol>
 *
 * <p>映射约定：表字段 snake_case 与实体驼峰字段由 MyBatis-Plus
 * 的 map-underscore-to-camel-case（默认开启）自动映射。
 */
@Data
@TableName("product")
public class Product {

    /** 商品ID：数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品名称 */
    private String productName;

    /** 副标题/核心卖点 */
    private String subtitle;

    /** 主图 URL */
    private String mainImage;

    /** 商品详情（富文本） */
    private String detail;

    /**
     * 原价（市场价）。
     * 金额一律用 BigDecimal，禁止 double/float，避免二进制浮点精度丢失。
     */
    private BigDecimal originalPrice;

    /** 状态：0-下架 1-上架（商品上下架与秒杀活动状态相互独立） */
    private Integer status;

    /** 创建时间：插入时由自动填充处理器写入 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间：插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-正常 1-已删除（@TableLogic 使 delete 语句自动变为 update） */
    @TableLogic
    private Integer deleted;
}