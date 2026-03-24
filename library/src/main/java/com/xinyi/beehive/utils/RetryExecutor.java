package com.xinyi.beehive.utils;

import android.os.SystemClock;

import androidx.annotation.WorkerThread;

import java.util.concurrent.Callable;

/**
 * 重试执行工具
 *
 * <p> 用于执行「操作 + 状态校验」类逻辑，在校验失败时按指定次数进行重试 </p>
 *
 * <h3> ⚠️ 注意： </h3>
 * <p>
 *   本类所有方法均为阻塞方法，会调用 {@link SystemClock#sleep(long)}，
 *   必须在子线程（WorkerThread）中执行，禁止在主线程调用。
 * </p>
 *
 * @author 新一
 * @date 2025/12/29
 */
public final class RetryExecutor {

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRY = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    private static final long DEFAULT_INTERVAL_MS = 100L;

    /**
     * 私有构造，防止实例化
     */
    private RetryExecutor() {
        throw new UnsupportedOperationException("Cannot instantiate RetryExecutor");
    }

    /**
     * 执行带校验的重试逻辑（使用默认参数）
     *
     * <p>⚠️ 阻塞方法，请勿在主线程调用</p>
     *
     * @param action 实际执行的操作
     * @return true 表示在重试次数内成功；false 表示最终失败
     */
    @WorkerThread
    public static boolean execute(Callable<Boolean> action) {
        return execute(DEFAULT_MAX_RETRY, DEFAULT_INTERVAL_MS, action);
    }

    /**
     * 执行带校验的重试逻辑
     *
     * <p>⚠️ 阻塞方法，请勿在主线程调用</p>
     *
     * @param maxRetry  最大重试次数（包含首次执行）
     * @param intervalMs 每次重试之间的间隔（毫秒）
     * @param action 实际执行的操作
     * @return true 表示在重试次数内成功；false 表示最终失败
     */
    @WorkerThread
    public static boolean execute(int maxRetry, long intervalMs, Callable<Boolean> action) {
        if (maxRetry <= 0) {
            throw new IllegalArgumentException("maxRetry must be greater than 0");
        }
        if (action == null) {
            throw new NullPointerException("action == null");
        }
        for (int attempt = 0; attempt < maxRetry; attempt++) {
            try {
                if (Boolean.TRUE.equals(action.call())) {
                    return true;
                }
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
            }
            // 非最后一次才等待
            if (attempt < maxRetry - 1) {
                SystemClock.sleep(intervalMs);
            }
        }
        return false;
    }
}