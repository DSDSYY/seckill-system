package com.example.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体 —— 对应表 seckill_activity
 *
 * <p>为什么库存放"活动表"而不是"商品表"：
 * <ol>
 *   <li>秒杀库存/秒杀价都是"活动属性"，不同轮次活动库存与价格相互独立；
 *   <li>扣减库存只命中活动这一行，行锁粒度小、SQL 简单，天然支持原子扣减。
 * </ol>
 *
 * <p>库存字段分工：
 * <ul>
 *   <li>total_stock 总库存：活动创建时写入后不再修改，用于展示进度/对账；
 *   <li>seckill_stock 剩余库存：真正参与并发扣减的计数器，是防超卖的核心字段。
 * </ul>
 */
@Data
@TableName("seckill_activity")
public class SeckillActivity {

    /** 秒杀活动ID：数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联商品ID（product.id） */
    private Long productId;

    /** 秒杀价（BigDecimal 保证金额精度） */
    private BigDecimal seckillPrice;

    /**
     * 秒杀剩余库存：防超卖核心字段。
     * 只允许通过条件 UPDATE 原子扣减：
     * UPDATE ... SET seckill_stock = seckill_stock - 1
     * WHERE id = ? AND seckill_stock > 0 AND deleted = 0
     */
    private Integer seckillStock;

    /** 秒杀总库存：创建后只读，用于展示"已抢 X%"与对账 */
    private Integer totalStock;

    /** 秒杀开始时间 */
    private LocalDateTime startTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 活动状态：0-未开始 1-进行中 2-已结束 3-已下架 */
    private Integer status;

    /**
     * 乐观锁版本号。
     * 主防线是"条件 UPDATE"，version 是兜底：当业务演进为
     * "先 Redis 扣减、异步批量落库"等复杂流程时，用 version 做 CAS，
     * 防止低版本数据覆盖高版本数据（丢失更新）。
     */
    @Version
    private Integer version;

    /** 创建时间：插入时由自动填充处理器写入 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间：插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;
}