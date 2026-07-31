package com.wuji.assistant.common.api;

/**
 * 统一 JSON 响应包装。
 *
 * @param code    业务码，成功为 OK
 * @param message 提示信息
 * @param data    载荷
 * @param <T>     数据类型
 * @author liudy
 */
public record ApiResponse<T>(String code, String message, T data) {

    /**
     * 成功响应。
     *
     * @param data 载荷
     * @param <T>  类型
     * @return 响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }

    /**
     * 失败响应。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param <T>     类型
     * @return 响应
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
