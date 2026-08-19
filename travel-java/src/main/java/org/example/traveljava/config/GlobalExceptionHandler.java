package org.example.traveljava.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 认证失败 → 401，前端自动跳转登录页 */
    @ExceptionHandler(AuthUtils.AuthException.class)
    public ResponseEntity<Result<Object>> handleAuthException(AuthUtils.AuthException e) {
        log.warn("认证失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(e.getMessage()));
    }

    /** 已登录但权限不足 → 403（不触发前端 401 登出逻辑） */
    @ExceptionHandler(AuthUtils.ForbiddenException.class)
    public ResponseEntity<Result<Object>> handleForbidden(AuthUtils.ForbiddenException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(e.getMessage()));
    }

    /** 缺少必需请求头（如 Authorization）→ 401 而非 500 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Object>> handleMissingHeader(MissingRequestHeaderException e) {
        if ("authorization".equalsIgnoreCase(e.getHeaderName())) {
            log.warn("缺少 Authorization 请求头: uri={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }
        log.warn("缺少请求头: {}", e.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail("缺少必需的请求头: " + e.getHeaderName()));
    }

    /** 缺少必需请求参数 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Object>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail("缺少必需的请求参数: " + e.getParameterName()));
    }

    /** JWT 过期 → 401 */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Result<Object>> handleExpiredJwt(ExpiredJwtException e) {
        log.warn("JWT已过期: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail("登录已过期，请重新登录"));
    }

    /** JWT 签名异常 → 401 */
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<Result<Object>> handleSignatureException(SignatureException e) {
        log.warn("JWT签名无效: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail("登录信息无效，请重新登录"));
    }

    /** JWT 格式错误 → 401 */
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<Result<Object>> handleMalformedJwt(MalformedJwtException e) {
        log.warn("JWT格式错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail("登录信息无效，请重新登录"));
    }

    /** 其他 JWT 异常 → 401 */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Result<Object>> handleJwtException(JwtException e) {
        log.warn("JWT无效: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail("登录信息无效，请重新登录"));
    }

    /** 文件大小超限 → 400，前端显示具体错误 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        long maxSize = e.getMaxUploadSize();
        String msg = maxSize > 0
                ? "文件大小超出限制，最大允许 " + (maxSize / 1024 / 1024) + "MB"
                : "文件大小超出限制";
        log.warn("文件上传超限: max={}", maxSize);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(msg));
    }

    /**
     * CTRL-2 修复：JSON 传字符串数字时 ((Number) x).intValue() 抛 ClassCastException，
     * 统一转 400 友好提示而非 500。
     */
    @ExceptionHandler(ClassCastException.class)
    public ResponseEntity<Result<Object>> handleClassCastException(ClassCastException e, HttpServletRequest request) {
        log.warn("参数类型不匹配: uri={}, error={}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail("参数类型不正确，请检查请求参数"));
    }

    /** 参数格式错误 → 400 */
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Result<Object>> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "参数不合法";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(msg));
    }

    /** 客户端连接中断（视频/文件下载时浏览器取消等）→ 不写响应，只记录 */
    // EXH-1 修复：只捕 ClientAbortException，不再笼统吞 IOException（真实 IO 故障应走通用异常处理）
    @ExceptionHandler({ClientAbortException.class})
    public void handleClientAbort(Exception e, HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted()) {
            // 响应已提交，无法修改，仅记录
            log.debug("客户端连接中断（响应已提交）: uri={}", request.getRequestURI());
        } else {
            log.debug("客户端连接中断: uri={}", request.getRequestURI());
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(Exception e, HttpServletRequest request,
                                                           HttpServletResponse response) {
        // 非JSON Content-Type（视频/文件流等）→ 不尝试写JSON，避免No converter错误
        String ct = response.getContentType();
        if (ct != null && !ct.contains("json") && !ct.contains("html") && !ct.contains("text")) {
            log.warn("非JSON响应异常: uri={}, contentType={}, error={}", request.getRequestURI(), ct, e.getMessage());
            try { response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value()); } catch (IOException ignored) {}
            return null;
        }
        log.error("系统异常: uri={}, error={}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("系统繁忙，请稍后重试"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("参数校验失败: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail("参数校验失败: " + errors.values().stream().findFirst().orElse("")));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Object>> handleNotFound(NoHandlerFoundException e) {
        log.warn("资源未找到: {}", e.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail("请求的资源不存在"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Object>> handleRuntimeException(RuntimeException e, HttpServletRequest request,
                                                                   HttpServletResponse response) {
        // 非JSON Content-Type → 跳过JSON转换，避免 "No converter for Result with preset Content-Type 'video/mp4'"
        String ct = response.getContentType();
        if (ct != null && !ct.contains("json") && !ct.contains("html") && !ct.contains("text")) {
            log.warn("非JSON响应运行时异常: uri={}, contentType={}, error={}", request.getRequestURI(), ct, e.getMessage());
            try { response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value()); } catch (IOException ignored) {}
            return null;
        }
        // SSE 超时不走 JSON 转换
        if (e instanceof org.springframework.web.context.request.async.AsyncRequestTimeoutException) {
            log.info("SSE超时: {}", request.getRequestURI());
            return ResponseEntity.ok().build();
        }
        log.error("运行时异常: uri={}, error={}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail("系统繁忙，请稍后重试"));
    }
}
