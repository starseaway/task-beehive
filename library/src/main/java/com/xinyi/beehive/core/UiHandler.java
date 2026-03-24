package com.xinyi.beehive.core;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

/**
 * UI线程的Handler处理类
 *
 * @author 新一
 * @date 2025/3/18 11:24
 */
public class UiHandler extends Handler {

    /**
     * 创建一个 UIHandler 对象
     */
    public static UiHandler createHandler() {
        return new UiHandler();
    }

    /**
     * Ui 线程的消息处理回调接口
     */
    private IUiHandlerCallback mUiHandlerCallback;

    /**
     * 狗仔方法
     */
    public UiHandler() {
        super(Looper.getMainLooper());
    }

    /**
     * 在 UI 线程中运行指定的任务，支持延迟执行
     *
     * @param task 要执行的任务
     * @param duration 延迟执行的时间（毫秒）
     */
    public synchronized void runOnUiThread(Runnable task, long duration) {
        if (task == null) {
            return;
        }
        removeCallbacks(task);
        if (duration > 0 || Thread.currentThread() != getLooper().getThread()) {
            postDelayed(task, duration);
        } else {
            task.run();
        }
    }

    /**
     * 设置 UI 线程消息处理回调接口
     *
     * @param callback 回调接口
     */
    public void setUiHandlerCallback(IUiHandlerCallback callback) {
        mUiHandlerCallback = callback;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        if (mUiHandlerCallback != null) {
            mUiHandlerCallback.handleUiMessage(msg);
        }
    }

    /**
     * UI线程消息处理回调接口
     *
     * <p>
     *   当 {@link android.os.Message} 没有设置 `Callback` 时，该接口将作为消息的最终处理者。
     *   在 `dispatchMessage` 方法中，如果handleMessage返回 `true`，则消息不会继续向下分发，该回调也不在触发。
     * </p>
     *
     * <p>
     *   参见 {@link android.os.Handler.Callback#handleMessage(Message)} 了解有返回值的 `handleMessage` 方法的作用
     * </p>
     *
     * <p>
     *   参见 {@link android.os.Handler#dispatchMessage(Message)} 了解 Handler 消息分发流程。
     * </p>
     */
    public interface IUiHandlerCallback {

        /**
         * 处理 UI 线程的消息
         *
         * @param msg 接收到的消息对象
         */
        void handleUiMessage(Message msg);
    }
}