package com.example.seckill.service;

/**
 * 秒杀核心服务接口（请求入口链路）
 */
public interface SeckillService {

    /**
     * 初始化/预热秒杀库存：把活动表剩余库存写入 Redis（Key: seckill:stock:{id}）
     *
     * @param activityId 秒杀活动ID
     * @return true 预热成功
     */
    boolean initStock(Long activityId);

    /**
     * 执行秒杀（请求入口）：
     * ① 活动合法性校验 -> ② SETNX 一人一单 -> ③ Redis Lua 原子扣减
     * -> ④ 生成临时订单号 -> ⑤ 投递 RabbitMQ（失败则回补库存+释放资格）
     *
     * @param userId     用户ID
     * @param activityId 秒杀活动ID
     * @return 临时订单号（异步落库前用户拿到的凭证，前端轮询订单状态）
     */
    String doSeckill(Long userId, Long activityId);

    /**
     * 查看 Redis 中某活动剩余库存（测试/联调用）
     *
     * @return 剩余库存；Key 不存在返回 null（说明尚未预热）
     */
    Integer getRemainingStock(Long activityId);
}