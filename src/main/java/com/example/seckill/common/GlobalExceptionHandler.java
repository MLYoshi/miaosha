package com.example.seckill.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * - MethodArgumentNotValidException：@Valid 校验失败，返回 HTTP 400 + 友好错误信息
 * - MiaoshaException：业务异常，返回 HTTP 200 + Result.error(code, msg)，保持与现有前端约定一致
 * - Exception：兜底异常，返回 HTTP 200 + Result.error(SERVER_ERROR)，防止堆栈泄露
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数校验失败（@Valid 触发）
     * 取第一个字段错误的 message 作为友好提示，返回 HTTP 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, message));
    }

    /**
     * 业务异常，返回 HTTP 200 + 业务错误码
     * 前端通过 Result.code 判断业务结果，与现有约定保持一致
     */
    @ExceptionHandler(MiaoshaException.class)
    public Result<Void> handleMiaoshaException(MiaoshaException ex) {
        CodeMsg codeMsg = ex.getCodeMsg();
        log.info("业务异常: code={}, msg={}", codeMsg.getCode(), codeMsg.getMsg());
        return Result.error(codeMsg.getCode(), codeMsg.getMsg());
    }

    /**
     * 兜底异常处理，防止堆栈泄露给前端
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.error(CodeMsg.SERVER_ERROR.getCode(), CodeMsg.SERVER_ERROR.getMsg());
    }
}
