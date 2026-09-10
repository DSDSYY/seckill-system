package com.example.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.entity.Product;

/**
 * 商品 Mapper：异步下单时读取商品名称做订单快照
 */
public interface ProductMapper extends BaseMapper<Product> {
}