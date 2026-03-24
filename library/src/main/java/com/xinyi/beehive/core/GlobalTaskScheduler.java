package com.xinyi.beehive.core;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.xinyi.beehive.TaskBeehive;
import com.xinyi.beehive.utils.DualConsumer;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局任务调度器
 *
 * <p> 提供三类执行模型：</p>
 * <ul>
 *     <li> 串行执行：基于 Handler（post）</li>
 *     <li> 并发执行：基于线程池（submitAsync）</li>
 *     <li> 阻塞执行：同步等待结果（await）</li>
 * </ul>
 *
 * <p> 设计目标：</p>
 * <ul>
 *     <li> 统一线程调度入口 </li>
 *     <li> 明确同步 / 异步语义 </li>
 *     <li> 防止任务丢失（背压机制） </li>
 * </ul>
 *
 * ⚠️ 所有 blocking 方法禁止在主线程调用
 *
 * @author 新一
 * @date 2026/3/20 8:45
 */
public class GlobalTaskScheduler {

    /**
     * 线程池名称
     */
    private static final String THREAD_POOL_NAME = "GlobalTaskScheduler-Pool";

    /**
     * Handler 线程名称
     */
    private static final String THREAD_HANDLER_NAME = "GlobalTaskScheduler-Handler";

    /**
     * 线程处理器，支持工作线程和 UI 线程切换，内置 Handler，支持串行执行处理任务
     */
    private static ThreadHandler sThreadHandler;

    /**
     * 线程池，支持高并发执行
     */
    private static volatile ExecutorService sThreadPool;

    /**
     * 线程编号生成器（用于命名）
     */
    private static final AtomicInteger sThreadId = new AtomicInteger(1);

    private GlobalTaskScheduler() { }

    /**
     * 获取线程处理器
     */
    public static ThreadHandler getThreadHandler() {
        if (sThreadHandler == null) {
            sThreadHandler = ThreadHandler.createHandler(THREAD_HANDLER_NAME);
        }
        return sThreadHandler;
    }

    /**
     * 获取工作线程 Handler
     */
    public static WorkerHandler getWorkerHandler() {
        return getThreadHandler().getWorkerHandler();
    }

    /**
     * 获取 UI 线程 Handler
     */
    public static UiHandler getUiHandler() {
        return getThreadHandler().getUiHandler();
    }

    /**
     * 在 UI 线程中执行任务
     *
     * @param task 要执行的任务
     */
    public static void runUiTask(@NonNull Runnable task) {
        getThreadHandler().runOnUiThreadTask(task);
    }

    /**
     * 在 Handler 工作线程中执行任务
     *
     * @param task 要执行的任务
     */
    public static void runWorkTask(@NonNull Runnable task) {
        getThreadHandler().runOnWorkThreadTask(task);
    }

    /**
     * 在 Handler 工作线程中执行延迟任务
     *
     * @param task 要执行的任务
     * @param delayMillis 延迟时间
     */
    public static void runWorkTaskDelayed(@NonNull Runnable task, long delayMillis) {
        getThreadHandler().runOnWorkThreadTask(task, delayMillis);
    }

    /**
     * 获取全局线程池
     *
     * <p> 特性：</p>
     * <ul>
     *     <li> 优先入队，其次扩容线程 </li>
     *     <li> 不丢任务，过载时调用线程执行 </li>
     * </ul>
     */
    public static ExecutorService getThreadPool() {
        if (sThreadPool == null) {
            synchronized (GlobalTaskScheduler.class) {
                if (sThreadPool == null) {
                    // CPU核心数
                    int core = Runtime.getRuntime().availableProcessors();
                    // 最大池数 core * 2
                    int max = core * 2;

                    sThreadPool = new ThreadPoolExecutor(
                            core,
                            max,
                            60L,
                            TimeUnit.SECONDS,
                            // 有界队列 128
                            new LinkedBlockingQueue<>(128),
                            runnable -> new Thread(runnable, THREAD_POOL_NAME + "-"
                                            + sThreadId.getAndIncrement()),
                            // 拒绝策略 = CallerRunsPolicy（背压）
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                }
            }
        }
        return sThreadPool;
    }

    /**
     * 在线程池中执行任务
     *
     * @param task 要执行的任务
     */
    public static Future<?> submitAsync(@NonNull Runnable task) {
        return getThreadPool().submit(task);
    }

    /**
     * 在线程池中执行任务
     *
     * @param task 要执行的任务
     */
    public static <V> Future<V> submitAsync(@NonNull Callable<V> task) {
        return getThreadPool().submit(task);
    }

    /**
     * 提交任务并忽略异常
     *
     * @param task 要执行的任务
     */
    public static void submitAsyncSafe(@NonNull Runnable task) {
        getThreadPool().submit(() -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                throwable.printStackTrace(System.err);
            }
        });
    }

    /**
     * 并行执行多个任务，全部完成后回调
     *
     * <p> 当 tasks 为空时，立即回调 </p>
     */
    public static void submitAsyncSafeAll(@NonNull Runnable[] tasks, @NonNull Runnable onComplete) {
        if (tasks.length == 0) {
            onComplete.run();
            return;
        }
        AtomicInteger count = new AtomicInteger(tasks.length);
        for (Runnable task : tasks) {
            getThreadPool().submit(() -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    throwable.printStackTrace(System.err);
                } finally {
                    if (count.decrementAndGet() == 0) {
                        onComplete.run();
                    }
                }
            });
        }
    }

    /**
     * 阻塞执行任务并返回结果
     *
     * <p> 当前线程将被阻塞，直到任务执行完成 </p>
     *
     * @param task 要执行的任务
     */
    @WorkerThread
    public static <V> V await(@NonNull Callable<V> task) throws ExecutionException, InterruptedException {
        return getThreadPool().submit(task).get();
    }

    /**
     * 阻塞执行任务，支持超时判断
     */
    @WorkerThread
    public static <V> V await(@NonNull Callable<V> task, long timeoutMs) throws ExecutionException, InterruptedException, TimeoutException {
        return submitAsync(task).get(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 在线程池中执行任务，并返回结果。支持状态回调
     *
     * @param task 要执行的任务
     * @param callback 回调 (状态码, 错误信息）
     *                 0 - 任务执行成功
     *                 1 - 任务执行失败
     */
    @WorkerThread
    public static <V> V await(@NonNull Callable<V> task, DualConsumer<Integer, Exception> callback) {
        try {
            V result = await(task);
            callback.accept(0, null);
            return result;
        } catch (ExecutionException | InterruptedException exception) {
            callback.accept(1, exception);
            return null;
        }
    }

    /**
     * 在指定超时时间内运行一个任务，如果超时则不中断，让其继续在后台运行并忽略状态回调
     *
     * @param task 要执行的任务
     * @param timeoutMs 超时时间（毫秒）
     */
    public static void runWithTimeoutThenIgnore(Runnable task, long timeoutMs) {
        TaskBeehive.ensureNotMainThread();
        runWithTimeout(task, timeoutMs, null);
    }

    /**
     * 在指定超时时间内运行一个任务，如果超时则不中断，让其继续在后台运行，但不忽略状态回调
     *
     * @param task 要执行的任务
     * @param timeoutMs 超时时间（毫秒）
     * @param callback 回调 (状态码, 错误信息)，成功时错误信息为 null
     *                 <li> 0 - 任务执行成功 </li>
     *                 <li> 1 - 任务执行超时 </li>
     *                 <li> 2 - 任务执行失败 </li>
     */
    public static void runWithTimeout(Runnable task, long timeoutMs, DualConsumer<Integer, Exception> callback) {
        TaskBeehive.ensureNotMainThread();

        Future<?> future = submitAsync(task);
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (callback != null) {
                callback.accept(0, null);
            }
        } catch (TimeoutException ex) {
            if (callback != null) {
                callback.accept(1, ex);
            }
            // 不取消任务，允许其继续后台运行
            Log.i(TaskBeehive.class.getSimpleName(), "任务执行超过 " + timeoutMs + " 毫秒，已转为后台执行。", ex);
        } catch (Exception ex) {
            if (callback != null) {
                callback.accept(2, ex);
            }
            ex.printStackTrace(System.err);
        }
    }

    /**
     * 判断当前线程是否为全局的线程池
     */
    public static boolean isGlobalThreadPool() {
        return Thread.currentThread().getName().startsWith(THREAD_POOL_NAME);
    }

    /**
     * 判断当前线程是否为全局的 Handler 工作线程
     */
    public static boolean isGlobalThreadHandler() {
        return Thread.currentThread().getName().startsWith(THREAD_HANDLER_NAME);
    }
}