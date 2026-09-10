package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 秒杀活动 Mapper
 *
 * <p>继承 {@link BaseMapper} 获得单表 CRUD；以下两条"条件更新"是数据库层
 * 防超卖/回滚库存的最终兜底 SQL（由异步消费者在事务内调用）。
 */
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * DB 兜底扣库存：只有 seckill_stock > 0 才扣。
     *
     * @return 影响行数：1=扣减成功；0=库存不足（事务将回滚，不落订单）
     */
    @Update("UPDATE seckill_activity " +
            "SET seckill_stock = seckill_stock - 1, version = version + 1 " +
            "WHERE id = #{activityId} AND seckill_stock > 0 AND deleted = 0")
    int deductStock(@Param("activityId") Long activityId);

    /**
     * 回滚库存（超时未支付取消订单时调用），与下单扣减一一对应。
     */
    @Update("UPDATE seckill_activity " +
            "SET seckill_stock = seckill_stock + 1, version = version + 1 " +
            "WHERE id = #{activityId} AND deleted = 0")
    int rollbackStock(@Param("activityId") Long activityId);
}