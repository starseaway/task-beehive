package com.xinyi.beehive;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.WorkerThread;

import com.xinyi.beehive.core.DelayTaskScheduler;
import com.xinyi.beehive.core.GlobalTaskScheduler;
import com.xinyi.beehive.core.PeriodicTaskScheduler;
import com.xinyi.beehive.core.ResettableDelayScheduler;
import com.xinyi.beehive.core.TimeOrderedEventLoop;

import java.util.concurrent.Callable;

/**
 * 任务调度框架门面类
 *
 * <p> 框架使用前，需要初始化 Application 对象 </p>
 *
 * @author 新一
 * @date 2025/3/18 10:57
 */
public final class TaskBeehive {

    /**
     * Application 实例
     */
    private static Application sApplication;

    /**
     * 是否已经初始化了 Application 实例
     *
     * @return true 如果已初始化，否则 false
     */
    public static boolean isInitApplication() {
        return sApplication != null;
    }

    /**
     * 获取当前主应用的 Application 实例
     *
     * @return Application 实例
     * @throws IllegalStateException 如果未初始化
     */
    public static Application getApplication() {
        if (sApplication == null) {
            throw new IllegalStateException("TaskBeehive 未初始化，请先调用 init(Context) 方法！");
        }
        return sApplication;
    }

    /**
     * 设置 Application 实例
     *
     * @param context Context 实例
     */
    public static void init(Context context) {
        if (sApplication != null) {
            return;
        }
        sApplication = (Application) context.getApplicationContext();
    }

    /**
     * 判断当前是否在主线程
     */
    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /**
     * 确保当前是非主线程调用
     */
    public static void ensureNotMainThread() {
        if (isMainThread()) {
            throw new IllegalStateException("Blocking call on main thread!");
        }
    }

    /**
     * 获取全局周期任务调度器
     *
     * @return PeriodicTaskScheduler 实例
     */
    public static PeriodicTaskScheduler getPeriodicScheduler() {
        return PeriodicTaskScheduler.getInstance();
    }

    /**
     * 创建一个高精度延迟任务调度器
     *
     * @param coreSize 核心线程数
     * @return DelayTaskScheduler 实例
     */
    public static DelayTaskScheduler createDelayScheduler(int coreSize) {
        return new DelayTaskScheduler(coreSize);
    }

    /**
     * 创建一个支持重置延迟的任务调度器
     *
     * @param threadName 线程名称
     * @return ResettableDelayScheduler 实例
     */
    public static ResettableDelayScheduler createResettableDelayScheduler(String threadName) {
        return new ResettableDelayScheduler(threadName);
    }

    /**
     * 创建一个时间有序的单线程事件循环调度器
     *
     * @param threadName 线程名称
     * @return TimeOrderedEventLoop 实例
     */
    public static TimeOrderedEventLoop createTimeOrderedEventLoop(String threadName) {
        return new TimeOrderedEventLoop(threadName);
    }

    /**
     * 在 UI 线程执行任务
     *
     * @param task 任务
     */
    public static void runOnUi(Runnable task) {
        GlobalTaskScheduler.runUiTask(task);
    }

    /**
     * 在全局 Handler 工作线程执行任务（串行执行）
     *
     * @param task 任务
     */
    public static void runOnWorker(Runnable task) {
        GlobalTaskScheduler.runWorkTask(task);
    }

    /**
     * 在全局线程池中异步执行任务（并发执行）
     *
     * @param task 任务
     */
    public static void runAsyncSafe(Runnable task) {
        GlobalTaskScheduler.submitAsyncSafe(task);
    }

    /**
     * 超时等待方法（阻塞方法）
     *
     * @param condition 需要等待成立的条件（返回 true 表示条件满足）
     * @param timeoutMs 最大等待时间（毫秒）
     * @return true 表示在超时前条件成立；false 表示等待超时
     */
    @WorkerThread
    public static boolean waitUntil(Callable<Boolean> condition, long timeoutMs) {
        long startTime = SystemClock.elapsedRealtime();

        while (true) {
            try {
                if (condition.call()) {
                    // 条件满足，立即返回
                    return true;
                }
            } catch (Exception exception) {
                exception.fillInStackTrace();
            }
            if (SystemClock.elapsedRealtime() - startTime >= timeoutMs) {
                return false;
            }
            SystemClock.sleep(100L);
        }
    }
}