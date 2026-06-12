package com.example.seckill.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import com.example.seckill.domain.User;

@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserMapperTest {

  @Autowired
  private UserMapper userMapper;

  @Test
  void getById_existingMobile_returnsUser() {
    User user = userMapper.getById(13000000000L);

    assertNotNull(user);
    assertEquals(13000000000L, user.getId());
    assertEquals("user0", user.getNickname());
    assertNotNull(user.getPassword());
    assertNotNull(user.getSalt());
  }

  @Test
  void getById_nonExistingMobile_returnsNull() {
    assertNull(userMapper.getById(19999999999L));
  }
}