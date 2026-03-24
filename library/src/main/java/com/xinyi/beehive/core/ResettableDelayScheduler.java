package com.xinyi.beehive.core;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一个支持重置延迟的任务执行器
 *
 * <p>
 *   调用 {@link #execute(Runnable, long)} 时，会安排一个在指定延迟之后执行的任务。
 *   若在任务尚未执行前再次调用该方法，则会取消前一个计划任务并重新开始计时，
 *   确保只有最近一次调用对应的任务会被执行
 * <p>
 *
 * @author 杨耿雷
 * @date 2025/12/4 9:30
 */
public class ResettableDelayScheduler {

    /**
     * 用于调度延迟任务的线程池，仅使用单线程以保证任务顺序
     */
    private final ScheduledExecutorService mScheduler;

    /**
     * 保存当前已计划但尚未执行的任务句柄
     */
    private final AtomicReference<ScheduledFuture<?>> mFutureRef = new AtomicReference<>();

    /**
     * 构造一个新的 ResettableDelayExecutor
     * 默认使用单线程守护线程池执行任务
     */
    public ResettableDelayScheduler(String threadName) {
        this.mScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ResettableDelayExecutor（线程） - " + threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 安排一个在指定延迟之后执行的任务
     *
     * <p> 如果此前已经提交过尚未执行的延迟任务，则会取消旧任务，重新按新的延迟计时 </p>
     *
     * @param task  要执行的任务
     * @param delayMs 延迟时间，单位为毫秒，必须大于等于 0
     * @throws IllegalArgumentException 如果 delayMs 小于 0
     */
    public void execute(@NotNull Runnable task, long delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }

        // 取消旧任务
        ScheduledFuture<?> old = mFutureRef.getAndSet(null);
        if (old != null) {
            old.cancel(false);
        }

        // 提交新任务
        ScheduledFuture<?> future = mScheduler.schedule(() -> {
            try {
                task.run();
            } finally {
                // 执行结束后清空引用，避免误取消下一次任务
                mFutureRef.set(null);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        mFutureRef.set(future);
    }

    /**
     * 判断当前是否存在尚未执行的延迟任务
     *
     * <p> 返回 true 表示当前有一个已计划但尚未执行完毕、且未被取消的任务 </p>
     *
     * @return 是否存在待执行任务
     */
    public boolean hasPendingTask() {
        ScheduledFuture<?> future = mFutureRef.get();
        // future 不为 null、未取消、未执行完成，即视为存在待执行任务
        return future != null && !future.isCancelled() && !future.isDone();
    }

    /**
     * 取消当前已计划但尚未执行的延迟任务
     *
     * <p> 如果当前没有计划任务，则该方法不会有任何效果 </p>
     */
    public void cancel() {
        ScheduledFuture<?> future = mFutureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭内部使用的调度线程池
     */
    public void shutdown() {
        mScheduler.shutdown();
    }
}