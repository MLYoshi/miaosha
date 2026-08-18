package com.example.seckill.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seckill.cache.RedisKeyBuilder;
import com.example.seckill.common.JwtUtil;
import com.example.seckill.common.MD5Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基座：Testcontainers 单例 MySQL 8 + Redis 7，真实 HTTP（RANDOM_PORT）。
 *
 * <p>隔离策略：每个用例前 TRUNCATE 全部业务表 + Redis FLUSHALL（服务端事务无法从测试侧回滚）。
 *
 * <p>不起 RabbitMQ 容器：当前代码未使用 AMQP，自动配置不会在建连前失败。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  protected static final String PLAIN_PASSWORD = "123456";
  protected static final String USER_SALT = "1a2b3c";

  protected static final int CODE_SUCCESS = 0;
  protected static final int CODE_GOODS_NOT_EXIST = 500104;
  protected static final int CODE_MIAOSHA_REPEAT = 500212;
  protected static final int CODE_MIAOSHA_STOCK_EMPTY = 500214;
  protected static final int CODE_MIAOSHA_NOT_START = 500215;
  protected static final int CODE_MIAOSHA_OVER = 500216;
  protected static final int CODE_MOBILE_NOT_EXIST = 500501;
  protected static final int CODE_PASSWORD_ERROR = 500502;

  // 单例容器模式：static 块显式启动，全测试类共享，JVM 退出时由 Testcontainers shutdown hook 回收
  @ServiceConnection
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
          .withDatabaseName("miaosha")
          .withInitScript("schema.sql");

  @ServiceConnection(name = "redis")
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.0")).withExposedPorts(6379);

  static {
    MYSQL.start();
    REDIS.start();
  }

  @Autowired protected TestRestTemplate rest;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected StringRedisTemplate redisTemplate;
  @Autowired protected ObjectMapper objectMapper;

  @BeforeEach
  void resetState() {
    jdbc.execute("TRUNCATE TABLE miaosha_order");
    jdbc.execute("TRUNCATE TABLE order_info");
    jdbc.execute("TRUNCATE TABLE miaosha_goods");
    jdbc.execute("TRUNCATE TABLE goods");
    jdbc.execute("TRUNCATE TABLE miaosha_user");
    redisTemplate.execute(
        (RedisCallback<Object>)
            connection -> {
              connection.serverCommands().flushAll();
              return null;
            });
  }

  // ---------- HTTP 辅助 ----------

  protected ResponseEntity<String> get(String path, Long userId) {
    HttpHeaders headers = new HttpHeaders();
    if (userId != null) {
      headers.setBearerAuth(JwtUtil.generateToken(userId));
    }
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  /** 带鉴权的 POST（写操作契约：do_miaosha / 预热均为 POST）。 */
  protected ResponseEntity<String> post(String path, Long userId) {
    HttpHeaders headers = new HttpHeaders();
    if (userId != null) {
      headers.setBearerAuth(JwtUtil.generateToken(userId));
    }
    return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class);
  }

  protected ResponseEntity<String> postJson(String path, String json) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
  }

  protected JsonNode body(ResponseEntity<String> resp) {
    try {
      return objectMapper.readTree(resp.getBody());
    } catch (Exception e) {
      throw new AssertionError("响应不是合法 JSON: " + resp.getBody(), e);
    }
  }

  // ---------- Fixture ----------

  protected long insertUser(long mobile) {
    jdbc.update(
        "INSERT INTO miaosha_user (id, nickname, password, salt, register_date, login_count)"
            + " VALUES (?,?,?,?,NOW(),0)",
        mobile,
        "user" + mobile,
        MD5Util.inputPassToDbPass(PLAIN_PASSWORD, USER_SALT),
        USER_SALT);
    return mobile;
  }

  /** 插入秒杀商品（秒杀窗口默认为"进行中"），返回 goodsId。 */
  protected long insertGoods(String name, int stock) {
    return insertGoods(
        name, stock, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
  }

  protected long insertGoods(String name, int stock, LocalDateTime start, LocalDateTime end) {
    KeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO goods (goods_name, goods_title, goods_price, goods_stock)"
                      + " VALUES (?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          ps.setString(1, name);
          ps.setString(2, name + " 长标题");
          ps.setBigDecimal(3, new BigDecimal("9999.00"));
          ps.setInt(4, 100);
          return ps;
        },
        kh);
    long goodsId = kh.getKey().longValue();
    jdbc.update(
        "INSERT INTO miaosha_goods (goods_id, miaosha_price, stock_count, start_date, end_date)"
            + " VALUES (?,?,?,?,?)",
        goodsId,
        new BigDecimal("0.01"),
        stock,
        Timestamp.valueOf(start),
        Timestamp.valueOf(end));
    return goodsId;
  }

  protected void preheat(long goodsId, long operatorUserId) {
    ResponseEntity<String> resp = post("/admin/preheat?goodsId=" + goodsId, operatorUserId);
    assertThat(body(resp).get("code").asInt()).as("预热失败: %s", resp.getBody()).isEqualTo(0);
  }

  protected JsonNode doMiaosha(long userId, long goodsId) {
    return body(post("/miaosha/do_miaosha?goodsId=" + goodsId, userId));
  }

  // ---------- 对账断言辅助 ----------

  protected int dbStock(long goodsId) {
    Integer stock =
        jdbc.queryForObject(
            "SELECT stock_count FROM miaosha_goods WHERE goods_id=?", Integer.class, goodsId);
    return stock == null ? -1 : stock;
  }

  protected int orderCount() {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM order_info", Integer.class);
    return n == null ? -1 : n;
  }

  protected int miaoshaOrderCount() {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM miaosha_order", Integer.class);
    return n == null ? -1 : n;
  }

  protected int duplicateMiaoshaOrderCount() {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (SELECT user_id, goods_id FROM miaosha_order"
                + " GROUP BY user_id, goods_id HAVING COUNT(*) > 1) t",
            Integer.class);
    return n == null ? -1 : n;
  }

  protected int redisStock(long goodsId) {
    String v = redisTemplate.opsForValue().get(RedisKeyBuilder.stock(goodsId));
    return v == null ? -1 : Integer.parseInt(v);
  }

  protected String redisUserKey(long goodsId, long userId) {
    return redisTemplate.opsForValue().get(RedisKeyBuilder.user(goodsId, userId));
  }

  protected String redisResult(long goodsId, long userId) {
    return redisTemplate.opsForValue().get(RedisKeyBuilder.result(goodsId, userId));
  }
}
