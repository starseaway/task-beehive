package com.xinyi.beehive.core;

import androidx.annotation.NonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
     * 调度代数
     *
     * <p> 每次 execute 递增，用于丢弃已被取代的任务，避免 finally 误清新的 future </p>
     */
    private final AtomicLong mGeneration = new AtomicLong();

    /**
     * 构造函数
     *
     * @param threadNameSuffix 工作线程名后缀
     */
    public ResettableDelayScheduler(String threadNameSuffix) {
        this.mScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Beehive-ResettableDelay-" + threadNameSuffix);
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
    public void execute(@NonNull Runnable task, long delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }

        final long gen = mGeneration.incrementAndGet();
        final AtomicReference<ScheduledFuture<?>> self = new AtomicReference<>();

        ScheduledFuture<?> future = mScheduler.schedule(() -> {
            // 已被更新的 execute 取代
            if (mGeneration.get() != gen) {
                return;
            }
            try {
                task.run();
            } finally {
                // 仅当仍是当前代时清空，避免清掉后续已安排的 future
                if (mGeneration.get() == gen) {
                    mFutureRef.compareAndSet(self.get(), null);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        self.set(future);
        ScheduledFuture<?> prev = mFutureRef.getAndSet(future);
        if (prev != null) {
            prev.cancel(false);
        }
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
        return future != null && !future.isCancelled() && !future.isDone();
    }

    /**
     * 取消当前已计划但尚未执行的延迟任务
     *
     * <p> 如果当前没有计划任务，则该方法不会有任何效果 </p>
     */
    public void cancel() {
        mGeneration.incrementAndGet();
        ScheduledFuture<?> future = mFutureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭内部使用的调度线程
     */
    public void shutdown() {
        mGeneration.incrementAndGet();
        mScheduler.shutdown();
    }
}