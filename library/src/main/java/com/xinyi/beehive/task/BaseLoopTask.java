package com.xinyi.beehive.task;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.xinyi.beehive.proxy.ThreadHandlerProxy;
import com.xinyi.beehive.core.ThreadHandler;

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
        if (mThreadHandler == null) {
            mThreadHandler = ThreadHandler.createHandler(this, getTaskName());
            mThreadHandler.getWorkerHandler().sendEmptyMessage(0);
        }
    }

    @Override
    public void recycleTask() {
        isRunning = false;
        isPaused = false;
        if (mThreadHandler == null) {
            return;
        }
        mThreadHandler.quitSafely();
        mThreadHandler = null;
        mListener = null;
    }

    /**
     * 发送延迟空消息
     */
    private void sendEmptyMessageDelayed() {
        if (getWorkerHandler() != null) {
            getWorkerHandler().sendEmptyMessageDelayed(0, getLoopDelay());
        }
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        runTask();
        return false;
    }

    @Override
    public void runTask() {
        // 任务终止 或 暂停 则不执行
        if (!isRunning || isPaused) {
            sendEmptyMessageDelayed();
            return;
        }
        // 执行任务
        if (mListener != null) {
            long loopDelay = mListener.onLoopTask(getLoopDelay());
            if (loopDelay != getLoopDelay()) {
                setLoopDelay(loopDelay);
            }
            sendEmptyMessageDelayed();
        }
    }

    /**
     * 循环任务监听
     */
    public interface LoopTaskListener {

        /**
         * 循环任务，返回值为下一次循环的延迟时间
         *
         * @param loopDelay 循环延迟时间
         */
        long onLoopTask(long loopDelay);
    }
}