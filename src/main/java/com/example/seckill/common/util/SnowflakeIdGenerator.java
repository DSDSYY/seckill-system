package com.example.seckill.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器（单机版，线程安全）
 *
 * <p>为什么订单号不用数据库自增：
 * <ol>
 *   <li>秒杀要求"先给用户一个临时订单号、再异步落库"，应用层必须先于 DB 生成 ID；
 *   <li>自增 ID 会暴露销量，且分库分表后无法保证全局唯一；
 *   <li>雪花 ID 是 long，趋势递增，对索引友好。
 * </ol>
 *
 * <p>ID 结构：1bit 符号位(0) | 41bit 毫秒时间戳 | 5bit 数据中心 | 5bit 机器 | 12bit 同毫秒序列
 */
@Slf4j
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳：2024-01-01 00:00:00（自定义，越小可用越久） */
    private static final long TW_EPOCH = 1704067200000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);            // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);    // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);             // 4095

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT =
            SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;

    /** 同毫秒内序列号 */
    private long sequence = 0L;
    /** 上次生成 ID 的时间戳 */
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(@Value("${seckill.snowflake.worker-id:1}") long workerId,
                                @Value("${seckill.snowflake.datacenter-id:1}") long datacenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 超出范围 [0," + MAX_WORKER_ID + "]: " + workerId);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId 超出范围 [0," + MAX_DATACENTER_ID + "]: " + datacenterId);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("SnowflakeIdGenerator 初始化成功 workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /** 生成下一个全局唯一 ID（synchronized 保证单机内线程安全） */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨处理：回拨容忍 5ms 内自旋等待追平，超过则抛异常（宁可失败不可重复）
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > 5) {
                throw new IllegalStateException("系统时钟回拨过大: " + offset + "ms");
            }
            try {
                Thread.sleep(offset + 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待时钟回拨被中断", e);
            }
            timestamp = System.currentTimeMillis();
        }

        // 同一毫秒：序列 +1；序列耗尽则等待下一毫秒
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - TW_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}