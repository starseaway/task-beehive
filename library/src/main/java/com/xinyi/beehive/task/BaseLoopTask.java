package com.xinyi.beehive.task;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.xinyi.beehive.core.ThreadHandler;
import com.xinyi.beehive.core.WorkerHandler;
import com.xinyi.beehive.proxy.ThreadHandlerProxy;

/**
 * 循环任务基类封装
 *
 * <p>
 *   该任务类用于定时执行任务，
 *   默认循环延迟时间为 5 秒，可通过{@link #setLoopDelay(long)}方法设置。
 * </p>
 *
 * @author 新一
 * @date 2025/3/18 15:15
 */
public abstract class BaseLoopTask extends BaseTask implements ThreadHandlerProxy, Handler.Callback {

    /**
     * 默认的循环时间常量
     */
    public static final long DEFAULT_LOOP_DELAY = 5000;

    /**
     * 循环消息 what
     */
    private static final int MSG_LOOP = 0;

    /**
     * 线程处理器
     */
    private ThreadHandler mThreadHandler;

    /**
     * 循环延迟时间
     */
    private long mLoopDelay = DEFAULT_LOOP_DELAY;

    /**
     * 循环任务监听
     */
    private LoopTaskListener mListener;

    @Override
    public ThreadHandler getThreadHandler() {
        return mThreadHandler;
    }

    /**
     * 设置循环任务监听
     *
     * @param listener 循环任务监听
     *                 {@link LoopTaskListener}
     */
    public void setLoopTaskListener(LoopTaskListener listener) {
        this.mListener = listener;
    }

    /**
     * 设置默认的循环延迟时间
     */
    public long getLoopDelay() {
        if (mLoopDelay <= 0) {
            mLoopDelay = DEFAULT_LOOP_DELAY;
        }
        return mLoopDelay;
    }

    /**
     * 设置循环延迟时间
     *
     * @param loopDelay 循环延迟时间
     */
    public void setLoopDelay(long loopDelay) {
        if (loopDelay <= 0) {
            return;
        }
        this.mLoopDelay = loopDelay;
    }

    @Override
    public void startTask() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        isPaused = false;
        if (mThreadHandler == null) {
            mThreadHandler = ThreadHandler.createHandler(this, getTaskName());
        }
        scheduleLoop(0);
    }

    @Override
    public void pauseTask() {
        if (!isRunning || isPaused) {
            return;
        }
        isPaused = true;
        cancelPendingLoop();
    }

    @Override
    public void resumeTask() {
        if (!isRunning || !isPaused) {
            return;
        }
        isPaused = false;
        scheduleLoop(0);
    }

    @Override
    public void recycleTask() {
        isRunning = false;
        isPaused = false;
        cancelPendingLoop();
        if (mThreadHandler == null) {
            return;
        }
        mThreadHandler.quitSafely();
        mThreadHandler = null;
        mListener = null;
    }

    /**
     * 取消待执行的循环消息
     */
    protected void cancelPendingLoop() {
        WorkerHandler handler = getWorkerHandler();
        if (handler != null) {
            handler.removeMessages(MSG_LOOP);
        }
    }

    /**
     * 调度下一次循环
     *
     * @param delayMillis 延迟毫秒，0 表示立即
     */
    protected void scheduleLoop(long delayMillis) {
        WorkerHandler handler = getWorkerHandler();
        if (handler == null || !isRunning || isPaused) {
            return;
        }
        handler.removeMessages(MSG_LOOP);
        if (delayMillis <= 0) {
            handler.sendEmptyMessage(MSG_LOOP);
        } else {
            handler.sendEmptyMessageDelayed(MSG_LOOP, delayMillis);
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if (msg.what == MSG_LOOP) {
            runTask();
        }
        return false;
    }

    @Override
    public void runTask() {
        // 任务终止 或 暂停 则不执行
        if (!isRunning || isPaused) {
            return;
        }
        if (mListener == null) {
            return;
        }

        // 执行核心循环任务
        long nextDelay = mListener.onLoopTask(getLoopDelay());

        // 符合条件则更新默认间隔
        if (nextDelay > 0 && nextDelay != getLoopDelay()) {
            setLoopDelay(nextDelay);
        }
        if (isRunning && !isPaused) {
            scheduleLoop(nextDelay > 0 ? nextDelay : 0);
        }
    }

    /**
     * 循环任务监听
     */
    public interface LoopTaskListener {

        /**
         * 循环任务回调
         *
         * @param loopDelay 当前默认循环间隔（毫秒）
         * @return 下一次调度延迟（毫秒）：
         *         <ul>
         *           <li>{@code > 0}：按该值延迟后再回调；若与当前默认间隔不同，会更新默认间隔</li>
         *           <li>{@code <= 0}：立即再调度一次，且不修改默认间隔 </li>
         *         </ul>
         */
        long onLoopTask(long loopDelay);
    }
}