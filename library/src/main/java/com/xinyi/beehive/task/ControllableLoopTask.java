package com.xinyi.beehive.task;

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
 * <p> 周期等待与阻塞等待均通过返回下一次调度延迟实现，不在 Handler 线程上 sleep，避免占死消息队列。 </p>
 *
 * @author 新一
 * @date 2026/3/19 14:43
 */
public abstract class ControllableLoopTask extends BaseLoopTask implements BaseLoopTask.LoopTaskListener {

    /**
     * 轮询间隔
     *
     * <p> 用于暂停计时 / 执行阻塞时感知状态变化 </p>
     */
    private static final long POLL_TICK_MS = 100L;

    /**
     * 重置标志（用于中断当前周期）
     */
    private final AtomicBoolean mResetFlag = new AtomicBoolean(false);

    /**
     * 当前是否正在执行核心任务逻辑
     */
    private final AtomicBoolean mIsExecutingCore = new AtomicBoolean(false);

    /**
     * 周期计时器（支持暂停 / 恢复）
     */
    private final PauseableTimer mPeriodTimer = new PauseableTimer();

    /**
     * 任务当前执行阶段
     */
    private volatile Phase mPhase = Phase.IDLE;

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
     * 核心任务的异常信息收集
     *
     * @param isAuto 是否自动周期执行
     * @param throwable 异常信息
     */
    protected void onLoopException(boolean isAuto, Throwable throwable) { }

    /**
     * 是否允许执行
     */
    protected boolean canExecute() {
        return true;
    }

    @Override
    public void pauseTask() {
        mPeriodTimer.pause();
        super.pauseTask();
    }

    @Override
    public void resumeTask() {
        mPeriodTimer.resume();
        super.resumeTask();
    }

    @Override
    public void recycleTask() {
        mPhase = Phase.IDLE;
        mPeriodTimer.reset();
        mResetFlag.set(false);
        super.recycleTask();
    }

    /**
     * 自行调度下一次 tick，避免把周期剩余时间写回 {@link #getLoopDelay()} 默认间隔
     */
    @Override
    public void runTask() {
        if (!isRunning || isPaused) {
            return;
        }
        long nextDelay = onLoopTask(getLoopDelay());
        if (isRunning && !isPaused) {
            scheduleLoop(Math.max(0, nextDelay));
        }
    }

    /**
     * 状态机主循环
     */
    @Override
    public long onLoopTask(long loopDelay) {
        long period = providePeriod();
        if (period <= 0) {
            return loopDelay;
        }

        // 开启新一轮周期倒计时
        if (mPhase == Phase.IDLE) {
            mResetFlag.set(false);
            mPeriodTimer.start(period);
            mPhase = Phase.WAITING_PERIOD;
            return nextPeriodDelay();
        }

        // 周期倒计时
        if (mPhase == Phase.WAITING_PERIOD) {
            // resetNow 立即打断当前周期
            if (mResetFlag.get()) {
                mPeriodTimer.reset();
                mPhase = Phase.IDLE;
                return 0;
            }
            // 暂停计时，短轮询等待放行
            if (shouldPauseTimer()) {
                mPeriodTimer.pause();
                return POLL_TICK_MS;
            }
            // 推进计时；未到点则继续等，到点则进入阻塞门闩阶段
            mPeriodTimer.resume();
            mPeriodTimer.tick();
            if (!mPeriodTimer.isFinished()) {
                return nextPeriodDelay();
            }
            mPhase = Phase.BLOCKING;
        }

        // 阻塞阶段同样可打断
        if (mResetFlag.get()) {
            mPhase = Phase.IDLE;
            return 0;
        }
        // 未放行，则短轮询等待放行后进入核心
        if (shouldBlockExecute()) {
            return POLL_TICK_MS;
        }

        // 业务侧拒绝本次执行
        if (!canExecute()) {
            mPhase = Phase.IDLE;
            return loopDelay;
        }

        // 与 executeNow 互斥，避免核心逻辑并发
        if (!mIsExecutingCore.compareAndSet(false, true)) {
            return loopDelay;
        }

        try {
            executeCore();
        } catch (Throwable throwable) {
            onLoopException(true, throwable);
        } finally {
            mIsExecutingCore.set(false);
            // 执行完毕，回到 IDLE，等待开启下一周期
            mPhase = Phase.IDLE;
        }
        return loopDelay;
    }

    /**
     * 周期未结束时的下一次调度延迟
     *
     * <p> 短轮询以便感知 shouldPauseTimer，且不阻塞 Handler 队列 </p>
     */
    private long nextPeriodDelay() {
        long remaining = mPeriodTimer.getRemainingMillis();
        if (remaining <= 0) {
            return 0;
        }
        return Math.min(remaining, POLL_TICK_MS);
    }

    /**
     * 立即执行一次核心任务（跳过周期）
     *
     * @return true = 已成功跑完 {@link #executeCore()}；
     *         false = 未执行（门闩未开 / 不允许 / 并发占用）或执行中抛错
     */
    @WorkerThread
    public boolean executeNow() {
        // 阻塞中则立即放弃，不等待
        if (shouldBlockExecute()) {
            return false;
        }
        if (!canExecute()) {
            return false;
        }

        // 与 onLoopTask 互斥，避免核心逻辑并发
        if (!mIsExecutingCore.compareAndSet(false, true)) {
            return false;
        }

        try {
            executeCore();
            return true;
        } catch (Throwable throwable) {
            onLoopException(false, throwable);
            return false;
        } finally {
            mIsExecutingCore.set(false);
        }
    }

    /**
     * 立即重置当前周期
     *
     * <p> 若当前正在计时或阻塞阶段，将被立即中断并重新开始周期 </p>
     */
    public void resetNow() {
        mResetFlag.set(true);
        mPeriodTimer.reset();
        mPhase = Phase.IDLE;
        cancelPendingLoop();
        scheduleLoop(0);
    }

    /**
     * 当前是否正在执行核心逻辑
     *
     * <p> 注意：生命周期是否已启动请使用 {@link #isRunning()} </p>
     *
     * @return true = 正在执行核心逻辑
     */
    public boolean isExecutingCore() {
        return mIsExecutingCore.get();
    }

    /**
     * 任务当前执行阶段
     */
    private enum Phase {
        /** 待开启新周期 */
        IDLE,
        /** 周期倒计时中 */
        WAITING_PERIOD,
        /** 等待放行执行 */
        BLOCKING
    }
}