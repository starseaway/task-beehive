package com.xinyi.beehive.task;

import android.util.Log;

import com.xinyi.beehive.algo.TimeAlignmentAlgo;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 精准控时的定时器任务类，支持秒级，分级，时级，天级的控时规则。
 * 默认时间间隔为5秒，可通过{@link #setIntervalInSeconds(int)}方法设置。
 *
 * <p>
 *   对齐目标用墙钟（{@link System#currentTimeMillis()}）计算；
 *   实际等待由 {@link ScheduledExecutorService} 相对延迟调度（内部基于单调时钟），
 *   单次回调异常不会拖垮调度器。
 * </p>
 *
 * @author 新一
 * @date 2025/3/18 15:45
 */
public abstract class PreciseTimerTask extends BaseTask {

    /** 时间格式：年-月-日 时:分:秒 */
    public static final String FORMAT_YMD_HMS = "yyyy-MM-dd HH:mm:ss";

    /**
     * 时间间隔（单位：秒）
     */
    private int mIntervalInSeconds = 5;

    /**
     * 单线程调度器（可在 recycle 后重建）
     */
    private ScheduledExecutorService mScheduler;

    /**
     * 当前已提交、尚未触发的任务 {@link ScheduledFuture}
     * 
     * <p> 负责尽量取消还没跑起来的调度；取消拦不住时，靠 {@link #mGeneration} 兜底 </p>
     */
    private final AtomicReference<ScheduledFuture<?>> mFutureRef = new AtomicReference<>();

    /**
     * 调度代数：当前有效调度的版本号（每换一轮计划就 +1）。
     *
     * <p>
     *   每次 {@link #runTask()} 重排、或 pause / recycle 取消时都会递增。
     *   提交延迟任务时把当时的版本号一并带上；回调里若发现版本号已变，
     *   说明这轮计划已被作废（哪怕 Future.cancel 没拦住），直接丢弃、不再执行也不再续期。
     * </p>
     */
    private final AtomicLong mGeneration = new AtomicLong();

    /**
     * 任务执行的监听器
     */
    private OnTimerTaskListener mTaskListener;

    /**
     * 获取间隔时间（单位：秒）
     */
    public int getIntervalInSeconds() {
        if (mIntervalInSeconds <= 0) {
            // 默认时间间隔为5秒
            mIntervalInSeconds = 5;
        }
        return mIntervalInSeconds;
    }

    /**
     * 设置定时器的时间间隔
     *
     * @param intervalInSeconds 时间间隔（单位：秒）
     */
    public void setIntervalInSeconds(int intervalInSeconds) {
        if (intervalInSeconds <= 0) {
            return;
        }
        this.mIntervalInSeconds = intervalInSeconds;
    }

    /**
     * 设置任务执行的监听器
     *
     * @param listener 任务执行的监听器
     */
    public void setOnTimerTaskListener(OnTimerTaskListener listener) {
        this.mTaskListener = listener;
    }

    /**
     * 启动定时器任务
     */
    @Override
    public void startTask() {
        if (isRunning) {
            return;
        }

        // 启动定时器
        isRunning = true;
        isPaused = false;
        ensureScheduler();
        runTask();
    }

    @Override
    public void pauseTask() {
        if (!isRunning || isPaused) {
            return;
        }
        isPaused = true;
        cancelScheduled();
    }

    @Override
    public void resumeTask() {
        if (!isRunning || !isPaused) {
            return;
        }
        isPaused = false;
        ensureScheduler();
        runTask();
    }

    /**
     * 终止并回收定时器任务
     */
    @Override
    public void recycleTask() {
        if (!isRunning && mScheduler == null) {
            return;
        }
        isRunning = false;
        isPaused = false;

        cancelScheduled();
        shutdownScheduler();
    }

    /**
     * 立即按新间隔重新对齐调度（不停掉生命周期）
     * 
     * @param intervalInSeconds 新间隔时间（单位：秒）
     */
    public void refreshIntervalTime(int intervalInSeconds) {
        setIntervalInSeconds(intervalInSeconds);
        if (isRunning && !isPaused) {
            ensureScheduler();
            runTask();
        }
    }

    /**
     * 确保调度器已启动
     */
    private void ensureScheduler() {
        if (mScheduler == null || mScheduler.isShutdown()) {
            final String name = "Beehive-PreciseTimer-" + getTaskName();
            mScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /**
     * 关闭调度器
     */
    private void shutdownScheduler() {
        ScheduledExecutorService scheduler = mScheduler;
        mScheduler = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 取消当前待触发任务，并尝试 cancel 作废其尚未触发的 Future 回调
     */
    private void cancelScheduled() {
        mGeneration.incrementAndGet();
        ScheduledFuture<?> future = mFutureRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 计算下一次对齐点并提交相对延迟调度
     */
    @Override
    public void runTask() {
        if (!isRunning || isPaused) {
            return;
        }
        ensureScheduler();

        // 生成新的调度代数，并取消上一个任务
        final long gen = mGeneration.incrementAndGet();
        ScheduledFuture<?> prev = mFutureRef.getAndSet(null);
        if (prev != null) {
            prev.cancel(false);
        }

        // 计算下一次对齐点并提交相对延迟调度
        long wallNow = System.currentTimeMillis();
        long delayMs = TimeAlignmentAlgo.calculateIntervalTime(wallNow, getIntervalInSeconds());
        final long scheduledWallTime = wallNow + delayMs;

        // 打印下一次对齐时间
        SimpleDateFormat format = new SimpleDateFormat(FORMAT_YMD_HMS, Locale.getDefault());
        Log.d(getTaskName(), "任务下一次精准执行时间 = " + format.format(scheduledWallTime)
                + ", 时间戳 = " + scheduledWallTime);


        ScheduledFuture<?> future = mScheduler.schedule(() ->
                onFire(gen, scheduledWallTime), delayMs, TimeUnit.MILLISECONDS);
        mFutureRef.set(future);
    }

    /**
     * 延迟到期回调
     *
     * <p> 主要负责隔离异常，并按最新代数决定是否续期 </p>
     *
     * @param gen 提交本任务时的版本号
     * @param scheduledWallTime 计划触发的墙钟时间
     */
    private void onFire(long gen, long scheduledWallTime) {
        // 版本已变 / 已停 / 已暂停：视为废票，直接返回
        if (mGeneration.get() != gen || !isRunning || isPaused) {
            return;
        }

        try {
            long actualTime = System.currentTimeMillis();
            if (mTaskListener != null) {
                int nextInterval = mTaskListener.onTask(scheduledWallTime, actualTime);
                if (nextInterval > 0) {
                    mIntervalInSeconds = nextInterval;
                }
            }
        } catch (Throwable throwable) {
            Log.e(getTaskName(), "精准定时任务执行异常", throwable);
        } finally {
            // 仍是本轮版本才接着排下一次，避免废票回调又拉起新调度
            if (mGeneration.get() == gen && isRunning && !isPaused) {
                runTask();
            }
        }
    }

    /**
     * 定义任务执行的接口
     */
    public interface OnTimerTaskListener {

        /**
         * 执行任务的回调方法
         *
         * @param scheduledTime 计划执行时间（墙钟毫秒）
         * @param actualTime 实际执行时间（墙钟毫秒）
         * @return 下一次间隔（秒），<= 0 时保持原间隔
         */
        int onTask(long scheduledTime, long actualTime);
    }
}