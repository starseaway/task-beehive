package com.xinyi.beehive.core;

import android.os.Handler;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 高级延迟任务调度器（无阻塞 + 可中断 + 高精度）
 *
 * <p> 支持：</p>
 * <ul>
 *     <li> 延迟任务（高精度） </li>
 *     <li> 任务取消（CancelToken） </li>
 *     <li> 条件执行（Condition） </li>
 *     <li> 执行线程切换（Dispatcher） </li>
 * </ul>
 *
 * @author 新一
 * @date 2026/3/19 16:48
 */
public final class DelayTaskScheduler {

    /**
     * 全局调度线程（时间控制）
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 任务表（用于去重 / 管理）
     */
    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param coreSize 线程数量
     */
    public DelayTaskScheduler(int coreSize) {
        this.scheduler = new ScheduledThreadPoolExecutor(coreSize, runnable -> {
            Thread thread = new Thread(runnable, "SmartScheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 提交延迟任务
     *
     * @param taskId 任务ID（可选，可用于去重）
     * @param delayMs 延迟时间
     * @param condition 条件（为 true 时中断）
     * @param dispatcher 执行线程
     * @param action 任务逻辑
     */
    public CancelToken schedule(String taskId, long delayMs, Callable<Boolean> condition, TaskDispatcher dispatcher, Runnable action) {
        CancelToken token = new CancelToken();
        // 去重（有ID才处理）
        if (taskId != null) {
            cancel(taskId);
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (token.isCanceled()) {
                return;
            }
            try {
                if (condition != null && condition.call()) {
                    return;
                }
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
            }

            dispatcher.dispatch(() -> {
                if (token.isCanceled()) {
                    return;
                }
                action.run();
            });
        }, delayMs, TimeUnit.MILLISECONDS);

        // 绑定
        token.bind(future);

        if (taskId != null) {
            taskMap.put(taskId, future);
        }
        return token;
    }

    /**
     * 提交延迟任务，简化调用
     */
    public CancelToken schedule(long delayMs, TaskDispatcher dispatcher, Runnable action) {
        return schedule(null, delayMs, null, dispatcher, action);
    }

    /**
     * 提交延迟任务，简化调用
     */
    public CancelToken schedule(long delayMs, Runnable action) {
        return schedule(null, delayMs, null, TaskDispatcher.direct(), action);
    }

    /**
     * 取消任务
     */
    public void cancel(String taskId) {
        ScheduledFuture<?> future = taskMap.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 关闭调度器
     */
    public void shutdown() {
        scheduler.shutdown();
        taskMap.clear();
    }

    /**
     * 任务取消控制器
     */
    public final static class CancelToken {

        /**
         * 取消标记
         */
        private final AtomicBoolean canceled = new AtomicBoolean(false);

        /**
         * 绑定的调度任务
         */
        private volatile ScheduledFuture<?> future;

        /**
         * 绑定 Future（仅内部使用）
         */
        void bind(ScheduledFuture<?> future) {
            this.future = future;
        }

        /**
         * 取消任务
         */
        public void cancel() {
            canceled.set(true);
            if (future != null) {
                future.cancel(false);
            }
        }

        /**
         * 是否已取消
         */
        public boolean isCanceled() {
            return canceled.get();
        }
    }

    /**
     * 任务执行分发器
     */
    public interface TaskDispatcher {

        /**
         * 分发任务到指定线程执行
         */
        void dispatch(Runnable task);

        /**
         * 当前线程执行
         */
        static TaskDispatcher direct() {
            return Runnable::run;
        }

        /**
         * Handler 线程执行
         */
        static TaskDispatcher handler(Handler handler) {
            return handler::post;
        }

        /**
         * 线程池执行
         */
        static TaskDispatcher executor(Executor executor) {
            return executor::execute;
        }
    }
}