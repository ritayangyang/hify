package com.hify.common.exception;

import com.hify.common.web.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，是异常转 HTTP 响应的唯一顶层出口。
 * Controller 与中间层不写 try-catch 包装，统一在此转换为 Result.fail()。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：返回异常携带的 ErrorCode，message 允许业务侧自定义覆盖。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return build(errorCode, e.getMessage());
    }

    /**
     * @RequestBody @Valid 校验失败：PARAM_ERROR，message 拼接全部字段的具体校验信息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return build(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 请求体缺失/JSON 语法错误/枚举反序列化失败：客户端参数问题，归 400 而非 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return build(ErrorCode.PARAM_ERROR, "请求体格式错误或参数值非法");
    }

    /**
     * 路径/查询参数类型转换失败（如 id传成非数字）：400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型转换失败: {}", e.getMessage());
        return build(ErrorCode.PARAM_ERROR, "参数类型错误: " + e.getName());
    }

    /**
     * 静态资源/路由不存在（Spring 6 静态处理器对未命中路径抛 NoResourceFoundException）：
     * 归 404，不打 ERROR 日志（探测路径是常态，且 SPA 前端路由由后端资源解析器回退 index.html）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, "资源不存在");
    }

    /**
     * 兜底：未预期异常统一按 INTERNAL_ERROR 返回，堆栈只记日志不外泄。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统未预期异常", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDesc());
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        Result<Void> body = Result.fail(errorCode.getCode(), message);
        HttpStatus httpStatus = HttpStatus.resolve(errorCode.getCode());
        return ResponseEntity.status(httpStatus == null ? HttpStatus.INTERNAL_SERVER_ERROR : httpStatus)
                .body(body);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
