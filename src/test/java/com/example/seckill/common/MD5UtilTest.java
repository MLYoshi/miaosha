package com.example.seckill.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MD5UtilTest {

  @Test
  void inputPassToFormPass_knownPassword() {
    assertEquals("d3b1294a61a07da9b49b6e22b2cbd7f9",
        MD5Util.inputPassToFormPass("123456"));
  }

  @Test
  void inputPassToDbPass_matchesDbUser0() {
    assertEquals("b7797cce01b4b131b433b6acf4add449",
        MD5Util.inputPassToDbPass("123456", "1a2b3c"));
  }
}
