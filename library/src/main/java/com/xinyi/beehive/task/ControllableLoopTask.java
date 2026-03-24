package com.xinyi.beehive.task;

import android.os.SystemClock;

import androidx.annotation.WorkerThread;

import com.xinyi.beehive.utils.PauseableTimer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可控的循环任务抽象基类
 *
 * <p> 在 {@link BaseLoopTask} 基础上扩展以下能力：</p>
 *
 * <ul>
 *     <li> 周期调度（支持动态周期） </li>
 *     <li> 计时控制（暂停 / 恢复） </li>
 *     <li> 执行控制（阻塞 / 放行） </li>
 *     <li> 运行中断（reset） </li>
 *     <li> 立即执行（跳过周期） </li>
 * </ul>
 *
 * @author 新一
 * @date 2026/3/19 14:43
 */
public abstract class ControllableLoopTask extends BaseLoopTask implements BaseLoopTask.LoopTaskListener {

    /**
     * 重置标志（用于中断当前周期）
     */
    private final AtomicBoolean mResetFlag = new AtomicBoolean(false);

    /**
     * 当前任务是否正在执行核心逻辑
     */
    private final AtomicBoolean mIsRunning = new AtomicBoolean(false);

    /**
     * 周期计时器（支持暂停 / 恢复）
     */
    private final PauseableTimer mPeriodTimer = new PauseableTimer();

    /**
     * 构造函数
     */
    public ControllableLoopTask() {
        // onLoopTask 默认 1s 的间隔
        setLoopDelay(1000);
        setLoopTaskListener(this);
    }

    /**
     * 提供任务执行周期（毫秒）
     *
     * @return 周期时长（ms）
     */
    protected abstract long providePeriod();

    /**
     * 是否需要暂停计时器
     *
     * @return true = 暂停计时
     */
    protected boolean shouldPauseTimer() {
        return false;
    }

    /**
     * 是否阻塞核心执行
     *
     * <p> 当返回 true 时，任务将持续等待，不进入核心执行逻辑 </p>
     *
     * @return true = 阻塞执行
     */
    protected boolean shouldBlockExecute() {
        return false;
    }

    /**
     * 核心任务执行逻辑
     */
    @WorkerThread
    protected abstract void executeCore();

    /**
     * 工作逻辑的异常信息收集
     *
     * @param isAuto 是否自动周期执行
     * @param throwable 异常信息
     */
    protected void onLoopException(boolean isAuto, Throwable throwable) { }

    /**
     * 是否允许执行
     *
     * @return true = 允许执行
     */
    protected boolean canExecute() {
        return true;
    }

    @Override
    public long onLoopTask(long loopDelay) {
        long period = providePeriod();
        if (period <= 0) {
            return loopDelay;
        }

        // 周期未完成，直接返回
        if (!mPeriodTimer.isFinished()) {
            return loopDelay;
        }

        // 仅在进入新周期时清 reset 标志，避免提前清除导致 reset 信号丢失
        mResetFlag.set(false);

        mPeriodTimer.start(period);

        // 周期计时阶段
        while (!mPeriodTimer.isFinished()) {
            // reset 中断
            if (mResetFlag.get()) {
                mPeriodTimer.reset();
                return loopDelay;
            }
            // pause / resume 控制
            if (shouldPauseTimer()) {
                mPeriodTimer.pause();
            } else {
                mPeriodTimer.resume();
                mPeriodTimer.tick();
            }
            SystemClock.sleep(100);
        }

        // 执行阻塞阶段
        while (shouldBlockExecute()) {
            if (mResetFlag.get()) {
                return loopDelay;
            }
            SystemClock.sleep(100);
        }

        // 执行阶段
        if (!canExecute()) {
            return loopDelay;
        }

        // 防止 executeNow 并发执行
        if (!mIsRunning.compareAndSet(false, true)) {
            return loopDelay;
        }

        // 到达周期终点，执行核心控制逻辑
        try {
            executeCore();
        } catch (Throwable throwable) {
            onLoopException(true, throwable);
        } finally {
            mIsRunning.set(false);
        }
        return loopDelay;
    }

    /**
     * 立即执行一次核心任务（跳过周期）
     */
    @WorkerThread
    public void executeNow() {
        if (shouldBlockExecute()) {
            return;
        }
        if (!canExecute()) {
            return;
        }

        // 防并发
        if (!mIsRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            executeCore();
        } catch (Throwable throwable) {
            onLoopException(false, throwable);
        }  finally {
            mIsRunning.set(false);
        }
    }

    /**
     * 立即重置当前周期
     *
     * <p> 若当前正在计时或阻塞阶段，将被立即中断并重新开始周期 </p>
     */
    public void resetNow() {
        mResetFlag.set(true);
        // 立即生效
        mPeriodTimer.reset();
    }

    /**
     * 当前任务是否正在执行核心逻辑
     *
     * @return true = 正在执行
     */
    public boolean isRunning() {
        return mIsRunning.get();
    }
}