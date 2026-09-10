package com.example.seckill.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：交换机/队列/绑定 + 15 分钟延时取消（TTL + 死信队列实现）
 *
 * <p><b>为什么用 TTL + 死信实现"延时队列"而不是 Redis 过期监听：</b>
 * <ul>
 *   <li>RabbitMQ 死信机制成熟可靠：消息到期后由 broker 精准投递到取消队列，不依赖
 *       Redis keyspace notification（该通知默认关闭、且过期事件可能延迟/丢失）；
 *   <li>消息进入延时队列后即被持久化，应用重启不丢"待取消"任务；
 *   <li>无需额外插件（RabbitMQ 3.x 原生支持 per-queue TTL + DLX）。
 * </ul>
 *
 * <p><b>消息流转：</b>
 * <pre>
 *  下单成功
 *    ├─► 发送到 seckill.order.queue        → 消费者落库建单
 *    └─► 发送到 seckill.order.delay.queue  → 躺 15 分钟
 *          到期 → 死信(DLX) → seckill.order.cancel.queue → 取消订单+回滚库存
 *  任一处理失败/重试耗尽 → seckill.order.fail.queue（死信，人工处理）
 * </pre>
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    /** 业务直连交换机：下单 & 延时消息都发到这里 */
    public static final String ORDER_EXCHANGE = "seckill.order.exchange";

    /** 死信交换机：超时取消 / 处理失败统一收口 */
    public static final String DLX_EXCHANGE = "seckill.order.dlx.exchange";

    public static final String ORDER_QUEUE = "seckill.order.queue";          // 下单落库
    public static final String DELAY_QUEUE = "seckill.order.delay.queue";    // 15 分钟延时
    public static final String CANCEL_QUEUE = "seckill.order.cancel.queue";  // 超时取消
    public static final String FAIL_QUEUE = "seckill.order.fail.queue";      // 死信(人工)

    public static final String ROUTING_CREATE = "seckill.order.create";
    public static final String ROUTING_DELAY = "seckill.order.delay";
    public static final String ROUTING_CANCEL = "seckill.order.cancel";
    public static final String ROUTING_FAIL = "seckill.order.fail";

    /** 支付超时时间（分钟），读取 application.yml：seckill.order.pay-timeout-minutes */
    @Value("${seckill.order.pay-timeout-minutes:15}")
    private long payTimeoutMinutes;

    // ==================== 交换机 ====================

    @Bean
    public DirectExchange orderExchange() {
        // durable=true：broker 重启后交换机依然存在
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    // ==================== 队列 ====================

    /** 下单队列：消费者落库建单；处理失败/重试耗尽 -> 死信到 fail 队列 */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_FAIL)
                .build();
    }

    /** 延时队列：消息进来后"躺" payTimeoutMinutes 分钟，到期自动进死信 -> cancel 队列 */
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .ttl((int) (payTimeoutMinutes * 60 * 1000))   // 15 分钟（毫秒），ttl() 接收 int
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_CANCEL)
                .build();
    }

    /** 超时取消队列：消费者把"待支付"订单置为取消并回滚库存 */
    @Bean
    public Queue cancelQueue() {
        return QueueBuilder.durable(CANCEL_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(ROUTING_FAIL)
                .build();
    }

    /** 死信队列：所有处理失败的消息汇聚于此，供人工/对账处理 */
    @Bean
    public Queue failQueue() {
        return QueueBuilder.durable(FAIL_QUEUE).build();
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ROUTING_CREATE);
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(orderExchange()).with(ROUTING_DELAY);
    }

    @Bean
    public Binding cancelBinding() {
        return BindingBuilder.bind(cancelQueue()).to(dlxExchange()).with(ROUTING_CANCEL);
    }

    @Bean
    public Binding failBinding() {
        return BindingBuilder.bind(failQueue()).to(dlxExchange()).with(ROUTING_FAIL);
    }

    // ==================== 消息转换器 ====================

    /**
     * JSON 消息转换器
     *
     * <p>为什么不用 JDK 默认序列化（Serializable）：
     * <ol>
     *   <li>二进制不可读，排查问题困难，且与 Java 类强耦合（跨语言消费不可能）；
     *   <li>Java 反序列化有安全风险（反序列化漏洞）；
     *   <li>JSON 消息体 + __TypeId__ 头，任何语言的消费者都能读懂。
     * </ol>
     * 通过 trust 白名单只允许反序列化本项目的消息类，兼顾安全。
     */
    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.example.seckill.mq");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}