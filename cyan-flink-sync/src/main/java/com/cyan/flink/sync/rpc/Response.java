package com.cyan.flink.sync.rpc;

/**
 * 响应结果
 *
 * @author cy.Y
 * @since 1.0.0
 */
public record Response<T> (long code, String message, T data, String traceId) {
}
