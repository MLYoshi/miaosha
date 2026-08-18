package com.example.seckill.controller;

import com.example.seckill.common.Result;
import com.example.seckill.domain.OrderInfo;
import com.example.seckill.service.MiaoshaAcceptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀 HTTP 入口，只做 HTTP 翻译：鉴头取 userId、参数解析、结果包装。
 *
 * <p>业务失败（{@code MiaoshaException}）统一由全局异常处理器转换为 Result.error，
 * 此处不做第二份转换。运营/管理动作（如库存预热）见 {@link AdminController}。
 */
@RestController
@RequestMapping("/miaosha")
public class MiaoshaController {

  private final MiaoshaAcceptService miaoshaAcceptService;

  public MiaoshaController(MiaoshaAcceptService miaoshaAcceptService) {
    this.miaoshaAcceptService = miaoshaAcceptService;
  }

  /** 秒杀主流程入口（写操作，仅 POST）：受理编排见 {@link MiaoshaAcceptService}。 */
  @PostMapping("/do_miaosha")
  public Result<OrderInfo> doMiaosha(HttpServletRequest request, @RequestParam Long goodsId) {
    Long userId = (Long) request.getAttribute("userId");
    return Result.success(miaoshaAcceptService.execute(userId, goodsId));
  }
}
