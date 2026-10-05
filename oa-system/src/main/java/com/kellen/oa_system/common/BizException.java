package com.kellen.oa_system.common;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>用于主动抛出可预期的业务错误（如"用户名或密码错误"），
 * 由全局异常处理器转为统一响应，不要在业务代码里 try-catch 后吞掉。</p>
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
