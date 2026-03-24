package com.xinyi.beehive.task;

import android.util.Log;

import com.xinyi.beehive.algo.TimeAlignmentAlgo;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 精准控时的定时器任务类，支持秒级，分级，时级，天级的控时规则。
 * 默认时间间隔为5秒，可通过{@link #setIntervalInSeconds(int)}方法设置。
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
     * 定时器对象，用于管理任务的调度
     */
    private Timer mTimer;

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
        mTimer = new Timer();

        // 运行定时任务
        runTask();
    }

    /**
     * 终止并回收定时器任务。
     */
    @Override
    public void recycleTask() {
        if (!isRunning) {
            return;
        }
        // 取消定时任务
        mTimer.cancel();
        mTimer = null;
        isRunning = false;
        isPaused = false;
    }

    /**
     * 立即刷新一次定时时间
     */
    public void refreshIntervalTime(int intervalInSeconds) {
        if (isRunning) {
            setIntervalInSeconds(intervalInSeconds);
            recycleTask();
            startTask();
        }
    }

    /**
     * 运行定时任务，计算下次调度的延迟时间，并通过定时器提交任务
     */
    @Override
    public void runTask() {
        // 获取当前时间
        long currentTime = System.currentTimeMillis();
        // 获取时间间隔和对齐规则
        long intervalInSeconds = getIntervalInSeconds();
        // 计算初次延迟时间（毫秒）
        long initialDelay = TimeAlignmentAlgo.calculateIntervalTime(currentTime, intervalInSeconds);
        // 计算计划执行时间
        long scheduledExecutionTime = currentTime + initialDelay;
        SimpleDateFormat format = new SimpleDateFormat(FORMAT_YMD_HMS, Locale.getDefault());
        String nextTime = format.format(scheduledExecutionTime);
        Log.d(getTaskName(), "任务下一次精准执行时间 = " + nextTime + ", 时间戳 = " + scheduledExecutionTime);

        // 提交定时任务
        mTimer.schedule(new PreciseTimerTaskRunner(this, scheduledExecutionTime), initialDelay);
    }

    /**
     * 定义任务执行的接口
     */
    public interface OnTimerTaskListener {
        /**
         * 执行任务的回调方法
         *
         * @param scheduledTime 计划执行时间
         * @param actualTime 实际执行时间
         */
        int onTask(long scheduledTime, long actualTime);
    }

    /**
     * 静态内部类，用于执行定时任务，避免匿名内部类隐式持有外部类引用导致的编译问题。
     */
    private static class PreciseTimerTaskRunner extends TimerTask {

        /**
         * 定时器任务对象
         */
        private final PreciseTimerTask mTask;

        /**
         * 计划执行时间
         */
        private final long mScheduledExecutionTime;


        public PreciseTimerTaskRunner(PreciseTimerTask task, long scheduledExecutionTime) {
            this.mTask = task;
            this.mScheduledExecutionTime = scheduledExecutionTime;
        }

        @Override
        public void run() {
            // 如果暂停或结束，直接重新安排下一个任务
            if (mTask.isPaused || !mTask.isRunning) {
                mTask.runTask();
                return;
            }

            // 记录任务执行的实际时间
            long actualTime = System.currentTimeMillis();

            // 执行任务的回调，允许调整下一次执行的间隔
            if (mTask.mTaskListener != null) {
                mTask.mIntervalInSeconds = mTask.mTaskListener.onTask(mScheduledExecutionTime, actualTime);
            }

            // 重新安排下一个任务
            mTask.runTask();
        }
    }
}