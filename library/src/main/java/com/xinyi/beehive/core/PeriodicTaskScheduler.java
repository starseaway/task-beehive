package com.xinyi.beehive.core;

import android.os.Build;
import android.os.Process;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 周期任务调度器
 *
 * <p>
 *   提供对 “周期性后台任务” 的统一调度与管理能力。
 *   支持任务的创建、启动、停止以及移除，并保证任务在后台线程池中稳定执行。
 * </p>
 *
 * <h3> 核心特性 </h3>
 * <ul>
 *     <li> 支持周期任务（基于 scheduleWithFixedDelay） </li>
 *     <li> 支持任务生命周期控制（start / stop / remove） </li>
 *     <li> 多任务并发执行（线程池驱动） </li>
 *     <li> 线程安全（支持多线程调用） </li>
 * </ul>
 *
 * <h3> 执行模型 </h3>
 * <ul>
 *     <li> 每个任务按 “执行完成后延迟 interval 再执行” 的模式运行 </li>
 *     <li> 不保证固定时间点触发，区别于 scheduleAtFixedRate（固定频率）</li>
 * </ul>
 *
 * @author 新一
 * @date 2025/12/25 20:35
 */
public class PeriodicTaskScheduler {

    /* ================================== 自带一个单例模式 ================================== */

    /**
     * 单例实例
     */
    private static volatile PeriodicTaskScheduler sInstance;

    /**
     * 获取调度器单例
     */
    public static PeriodicTaskScheduler getInstance() {
        if (sInstance == null) {
            synchronized (PeriodicTaskScheduler.class) {
                if (sInstance == null) {
                    sInstance = new PeriodicTaskScheduler(8);
                }
            }
        }
        return sInstance;
    }

    /* ================================== ============== ================================== */

    /**
     * 后台线程池
     */
    private final ScheduledThreadPoolExecutor mExecutor;

    /**
     * 任务 ID 生成器
     */
    private final AtomicInteger mTaskIdGen = new AtomicInteger(1);

    /**
     * 任务集合
     */
    private final Map<Integer, TaskHandle> mTaskMap = new ConcurrentHashMap<>();

    /**
     * 构造方法
     *
     * @param threadCount 后台线程数量
     */
    public PeriodicTaskScheduler(int threadCount) {
        mExecutor = new ScheduledThreadPoolExecutor(
            threadCount,
            new PriorityThreadFactory()
        );

        // 避免任务取消后残留
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mExecutor.setRemoveOnCancelPolicy(true);
        }
    }

    /**
     * 创建周期任务（不立即启动）
     *
     * @param intervalMs 周期时间（毫秒）
     * @param action 周期执行逻辑
     * @return 任务 ID
     */
    public int createTask(long intervalMs, Runnable action) {
        int id = mTaskIdGen.getAndIncrement();
        TaskHandle handle = new TaskHandle(intervalMs, action);
        mTaskMap.put(id, handle);
        return id;
    }

    /**
     * 启动指定任务
     *
     * <p>
     *   若任务已在运行，则不会重复启动
     * </p>
     */
    public void startTask(int id) {
        TaskHandle handle = mTaskMap.get(id);
        if (handle != null) {
            handle.start();
        }
    }

    /**
     * 停止指定任务（不移除）
     */
    public void stopTask(int id) {
        TaskHandle handle = mTaskMap.get(id);
        if (handle != null) {
            handle.stop();
        }
    }

    /**
     * 移除任务并释放资源
     */
    public void removeTask(int id) {
        TaskHandle handle = mTaskMap.remove(id);
        if (handle != null) {
            handle.stop();
        }
    }

    /**
     * 停止所有任务并关闭调度器
     */
    public void shutdown() {
        for (TaskHandle handle : mTaskMap.values()) {
            handle.stop();
        }
        mTaskMap.clear();
        mExecutor.shutdownNow();
    }

    /**
     * 单个任务控制对象
     */
    private class TaskHandle {

        /**
         * 周期时间
         */
        private final long intervalMs;

        /**
         * 任务逻辑
         */
        private final Runnable action;

        /**
         * 调度句柄
         */
        private ScheduledFuture<?> future;

        TaskHandle(long intervalMs, Runnable action) {
            this.intervalMs = intervalMs;
            this.action = action;
        }

        /**
         * 启动任务
         */
        synchronized void start() {
            if (future != null && !future.isCancelled()) {
                return;
            }
            future = mExecutor.scheduleWithFixedDelay(
                    action,
                    0,
                    intervalMs,
                    TimeUnit.MILLISECONDS
            );
        }

        /**
         * 停止任务
         */
        synchronized void stop() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }
    }

    /**
     * 后台线程工厂
     *
     * <p>
     *   提升线程优先级，避免前后台切换时被系统饿死
     * </p>
     */
    private static class PriorityThreadFactory implements ThreadFactory {

        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(() -> {
                // 提升线程优先级（非 UI）
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                runnable.run();
            });
            thread.setName("PersistentTask-" + mCount.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}