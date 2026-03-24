package com.xinyi.beehive.core;

import android.os.Build;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * <h1>无锁可控的工作逻辑循环线程模型</h1>
 *
 * <p> 实现统一管理线程生命周期，提供 start / pause / resume / stop 的线程控制能力 </p>
 * <p> 支持：【零锁线程控制（基于 CAS（Compare And Swap） + park/unpark）、暂停时完全不占 CPU、低延迟恢复执行】 </p>
 *
 * <h2> 线程生命周期时间线（Android / ART / Linux 调度视角） </h2>
 * <p>
 *   调度语义说明：
 *   <ul>
 *     <li><b>TASK_RUNNING</b>：线程处于可执行状态，可能正在运行，也可能在就绪队列中等待 CPU </li>
 *     <li><b>TASK_PARKED</b>：线程被 {@link LockSupport} 挂起，完全休眠，不参与调度 </li>
 *     <li><b>RUN QUEUE</b>：线程被系统抢占或主动让出 CPU 后进入调度队列 </li>
 *     <li><b>TASK_DEAD</b>：{@link #run()} 返回后，JVM Thread 结束 </li>
 *   </ul>
 * </p>
 * <pre>
 *     start()
 *        │
 *        ▼
 *     [TASK_RUNNING] 创建线程并进入可调度队列
 *        │
 *        ▼
 *     run() 循环
 *        │
 *        ├── doWorkLoop() 执行
 *        │
 *        ├── 被系统抢占
 *        │        │
 *        │        └──► [RUN QUEUE] ← 等待下一次 CPU 时间片
 *        │
 *        ▼
 *     pause()
 *        │
 *        ▼
 *     loop 检测到 STATE_PAUSED
 *        │
 *        ▼
 *     pausedFlag = true
 *     LockSupport.park()
 *        │
 *        ▼
 *     [TASK_PARKED]
 *       - 线程完全休眠
 *       - 不参与 CPU 调度
 *       - 不占用 CPU 时间片
 *
 *     resume()
 *        │
 *        ▼
 *     LockSupport.unpark(worker)
 *        │
 *        ▼
 *     [TASK_RUNNING] ← 被重新加入调度队列（不保证立刻执行）
 *        │
 *        ▼
 *     doWorkLoop() 继续执行
 *
 *     stop()
 *        │
 *        ▼
 *     STATE_STOPPED
 *     unpark(worker) ← 确保 park 状态下也能退出
 *        │
 *        ▼
 *     run() 循环结束
 *        │
 *        ▼
 *     onLoopExit()
 *        │
 *        ▼
 *     [TASK_DEAD] ← Linux 内核自动回收线程资源
 * </pre>
 *
 * @author 新一
 * @date 2026/2/4 9:35
 */
public abstract class WorkerLoop implements Runnable {

    /** 线程状态：运行中 */
    private static final int STATE_RUNNING = 0;

    /** 线程状态：暂停 */
    private static final int STATE_PAUSED = 1;

    /** 线程状态：已停止 */
    private static final int STATE_STOPPED = 2;

    /**
     * 当前线程状态
     */
    private final AtomicInteger workerState = new AtomicInteger(STATE_RUNNING);

    /**
     * 是否已启动
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * 线程已进入暂停态的确认标记
     *
     * <p> 用于实现 {@link #pause()} 完成时线程已经真正 park </p>
     */
    private final AtomicBoolean pausedFlag = new AtomicBoolean(false);

    /**
     * 工作线程
     */
    private Thread worker;

    /**
     * 线程名称
     */
    protected abstract String threadName();

    /**
     * 工作逻辑
     */
    protected abstract void doWorkLoop() throws Exception;

    /**
     * 工作逻辑的异常信息收集
     */
    protected void onLoopException(Throwable throwable) { }

    /**
     * 线程退出前的回调（用于安全释放资源）
     *
     * <p>注意：子类所有资源必须在这里释放 </p>
     */
    protected void onLoopExit() { }

    /**
     * 返回当前的工作线程
     */
    protected final Thread workerThread() {
        return worker;
    }

    /**
     * 启动线程
     */
    public void start() {
        if (started.getAndSet(true)) {
            return;
        }

        workerState.set(STATE_RUNNING);

        worker = new Thread(this, threadName());
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 暂停线程
     *
     * <p> 调用完成时保证【线程不会再进入新的 doWorkLoop<、线程已进入 park 状态】
     */
    public void pause() {
        if (!workerState.compareAndSet(STATE_RUNNING, STATE_PAUSED)) {
            return;
        }

        // 清除可能残存的暂停确认
        pausedFlag.set(false);

        // 等待工作线程确认本次 pause 并进入 park
        while (!pausedFlag.get()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 轻量级自旋等待
                Thread.onSpinWait();
            } else {
                // 低版本降级，让出时间片
                Thread.yield();
            }
        }
    }

    /**
     * 恢复线程执行
     */
    public void resume() {
        if (workerState.getAndSet(STATE_RUNNING) == STATE_RUNNING) {
            return;
        }
        unparkLoop();
    }

    /**
     * 停止线程
     */
    public void stop() {
        workerState.set(STATE_STOPPED);
        if (worker != null) {
            // 退出唤醒
            // 如果线程当前在 park，不执行 unpark 的话，它会永远睡眠无法退出
            unparkLoop();
        }
        started.set(false);
    }

    /**
     * 当前是否运行中
     */
    public boolean isRunning() {
        return workerState.get() == STATE_RUNNING;
    }

    /**
     * 当前是否暂停
     */
    public boolean isPaused() {
        return workerState.get() == STATE_PAUSED;
    }

    /**
     * 当前是否已停止
     */
    public boolean isStopped() {
        return workerState.get() == STATE_STOPPED;
    }

    /**
     * 是否已经 start 过
     */
    public boolean isStarted() {
        return started.get();
    }

    /**
     * 使工作线程进入休眠状态（无限期）
     */
    protected final void parkLoop() {
        LockSupport.park(this);
    }

    /**
     * 使工作线程进入限时休眠状态（纳秒级）
     *
     * @param nanos 休眠时间（Ns）
     */
    protected final void parkLoopNanos(long nanos) {
        LockSupport.parkNanos(this, nanos);
    }

    /**
     * 唤醒工作线程（若处于 park 状态）
     */
    protected final void unparkLoop() {
        if (worker != null) {
            // 如果线程被 park()，则唤醒线程
            LockSupport.unpark(worker);
        }
    }

    @Override
    public final void run() {
        try {
            for (;;) {
                int state = this.workerState.get();
                if (state == STATE_STOPPED) {
                    break;
                }
                if (state == STATE_PAUSED) {
                    // 标记线程已进入暂停态
                    pausedFlag.set(true);
                    // 使线程进入等待状态
                    parkLoop();
                    // 被唤醒后清除暂停标记
                    pausedFlag.set(false);
                    continue;
                }

                try {
                    doWorkLoop();
                } catch (Throwable throwable) {
                    onLoopException(throwable);
                }
            }
        } finally {
            // 线程退出前的统一收尾点
            try {
                onLoopExit();
            } finally {
                worker = null;
            }
        }
    }
}