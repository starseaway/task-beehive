package com.xinyi.beehive.utils;

import android.os.SystemClock;

/**
 * 可暂停 / 可恢复的计时器
 *
 * <p> 支持随时暂停、恢复，且时间不会漂移 </p>
 *
 * @author 新一
 * @date 2025/12/29 20:14
 */
public class PauseableTimer {

    /**
     * 剩余未消耗的时间（毫秒）
     *
     * <p> 当该值 <= 0 时，计时结束 </p>
     */
    private long mRemainingMillis;

    /**
     * 上一次扣减时间时的时间戳
     *
     * <p> 建议使用 SystemClock.elapsedRealtime()，避免系统时间被修改导致异常 </p>
     */
    private long mLastTickTime;

    /**
     * 是否处于暂停状态
     *
     * <p> 暂停状态下不会扣减 remainingMillis </p>
     */
    private boolean isPaused = false;

    /**
     * 开始一次新的计时
     *
     * @param durationMillis 需要计时的总时长（毫秒）
     */
    public void start(long durationMillis) {
        this.mRemainingMillis = durationMillis;
        // 记录起始时间点
        this.mLastTickTime = SystemClock.elapsedRealtime();
        this.isPaused = false;
    }

    /**
     * 重置计时器
     */
    public void reset() {
        mRemainingMillis = 0;
        isPaused = false;
        mLastTickTime = 0;
    }

    /**
     * 暂停计时
     *
     * <p> 暂停前会先结算一次已经流逝的时间，避免丢失时间片 </p>
     */
    public void pause() {
        if (!isPaused) {
            updateRemaining();
            isPaused = true;
        }
    }

    /**
     * 恢复计时
     *
     * <p> 恢复时重新记录时间戳，防止暂停期间的时间被错误扣减 </p>
     */
    public void resume() {
        if (isPaused) {
            mLastTickTime = SystemClock.elapsedRealtime();
            isPaused = false;
        }
    }

    /**
     * 判断计时是否已经完成
     */
    public boolean isFinished() {
        return mRemainingMillis <= 0;
    }

    /**
     * 获取当前剩余时间
     *
     * <p> 可用于日志或状态展示 </p>
     */
    public long getRemainingMillis() {
        return mRemainingMillis;
    }

    /**
     * 计时推进方法
     *
     * <p> 内部会根据真实流逝时间扣减 remainingMillis </p>
     */
    public void tick() {
        if (!isPaused) {
            updateRemaining();
        }
    }

    /**
     * 根据 elapsedRealtime 扣减剩余时间
     */
    private void updateRemaining() {
        long now = SystemClock.elapsedRealtime();
        long delta = now - mLastTickTime;
        if (delta > 0) {
            mRemainingMillis -= delta;
            mLastTickTime = now;
        }
    }
}