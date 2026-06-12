package com.example.seckill.controller;

import com.example.seckill.common.MiaoshaException;
import com.example.seckill.common.Result;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.service.MiaoshaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/miaosha")
public class MiaoshaController {

  private final MiaoshaService miaoshaService;

  public MiaoshaController(MiaoshaService miaoshaService) {
    this.miaoshaService = miaoshaService;
  }

  @GetMapping("/do_miaosha")
  public Result<OrderInfo> doMiaosha(
      HttpServletRequest request, @RequestParam Long goodsId) {
      Long userId = (Long) request.getAttribute("userId");
      try {
          return Result.success(miaoshaService.doMiaosha(userId, goodsId));
      } catch (MiaoshaException e) {
          return Result.error(e.getCodeMsg().getCode(), e.getCodeMsg().getMsg());
      }
  }
}
