package com.hify.common.exception;

import lombok.Getter;

/**
 * 错误码枚举，集中分段管理：
 * 4xx —— 客户端类错误；5xx —— 服务端类错误。
 * 字段固定为 code（对外返回的错误码）和 desc（默认错误描述）。
 */
@Getter
public enum ErrorCode {

    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),

    INTERNAL_ERROR(500, "系统内部错误");

    private final int code;
    private final String desc;

    ErrorCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
