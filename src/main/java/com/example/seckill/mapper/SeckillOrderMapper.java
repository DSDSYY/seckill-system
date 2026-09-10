package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.SeckillOrder;

/**
 * 秒杀订单 Mapper
 * 复杂状态流转（条件更新）在 Service 层用 LambdaUpdateWrapper 完成
 */
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
}