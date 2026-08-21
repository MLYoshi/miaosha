package com.example.seckill.message;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 消息层配置：定义下单请求 topic 并随应用启动自动创建。
 *
 * <p>已定决策：topic 名 {@value #ORDER_TOPIC}，分区 3、副本因子 1（KRaft 单节点）。
 * 生产/消费两侧的可靠性与 ack 语义见 {@code application.yaml} 的 {@code spring.kafka} 段。
 */
@Configuration
public class KafkaConfig {

  /** 下单请求 topic：秒杀请求排队、异步下单削峰的唯一入口 topic。 */
  public static final String ORDER_TOPIC = "seckill-order";

  private static final int PARTITIONS = 3;
  private static final short REPLICATION_FACTOR = 1;

  /**
   * 自动创建 {@code seckill-order} topic。
   *
   * <p>仅创建 topic 声明本身；若 broker 已存在同名 topic 则沿用既有分区/副本配置，
   * 不强制重设。副本因子 1 对应单 broker 部署（见 docker-compose 的 KRaft 单节点）。
   */
  @Bean
  public NewTopic seckillOrderTopic() {
    return new NewTopic(ORDER_TOPIC, PARTITIONS, REPLICATION_FACTOR);
  }
}
