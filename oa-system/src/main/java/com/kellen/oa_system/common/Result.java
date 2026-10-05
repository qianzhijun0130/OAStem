package com.kellen.oa_system.common;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 全局统一响应体。
 *
 * <p>所有 Controller 必须返回本类型，禁止裸返回实体或 Map，
 * 以保证前端可以用同一套拦截器解析成功/失败。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;

    private String message;

    private T data;

    private LocalDateTime timestamp;

    private Result() {
        this.timestamp = LocalDateTime.now();
    }

    public static <T> Result<T> success() {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(T data) {
        return build(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return build(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 用于校验失败等需要附带具体字段信息的场景 */
    public static <T> Result<T> fail(ErrorCode errorCode, String detail) {
        return build(errorCode.getCode(), detail, null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
