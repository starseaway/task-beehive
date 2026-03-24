package com.xinyi.beehive.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Printer;

import androidx.annotation.NonNull;

import com.xinyi.beehive.TaskBeehive;

/**
 * 工作线程Handler处理
 *
 * <p>
 *   封装了 Android 4.4 及以上版本中的 HandlerThread 和 Looper。
 *   可以用来处理耗时任务，避免阻塞 UI 线程。
 * </p>
 *
 * @author 新一
 * @date 2025/3/18 14:00
 */
public class WorkerHandler extends Handler {

    /**
     * 创建带有指定回调的 WorkerHandler
     *
     * @param name 线程名
     * @return 创建的 WorkerHandler 对象
     */
    public static WorkerHandler createHandler(String name) {
        return createHandler(null, name);
    }

    /**
     * 创建带有指定回调和打印机(Printer)的 WorkerHandler
     *
     * @param callback 回调对象
     * @param name 线程名
     * @return 创建的 WorkerHandler 对象
     */
    public static WorkerHandler createHandler(Callback callback, String name) {
        return createHandler(callback, null, name);
    }

    /**
     * 创建带有指定回调和打印机(Printer)的 WorkerHandler
     *
     * @param callback 回调对象
     * @param printer 打印机对象，用于记录日志
     * @param name 线程名
     * @return 创建的 WorkerHandler 对象
     */
    public static WorkerHandler createHandler(Callback callback, Printer printer, String name) {
        HandlerThread mThread = new HandlerThread(name);
        mThread.start();
        if (printer != null) {
            mThread.getLooper().setMessageLogging(printer);
        }
        return new WorkerHandler(mThread.getLooper(), callback);
    }

    /**
     * 工作线程的 ID
     */
    private final long mWorkerThreadID;

    /**
     * 工作线程消息处理回调接口
     */
    private IWorkerHandlerCallback mWorkerHandlerCallback;

    /**
     * @param looper 工作线程的 Looper
     * @param callback 回调对象
     */
    public WorkerHandler(Looper looper, Callback callback) {
        super(looper, callback);
        mWorkerThreadID = looper.getThread().getId();
    }

    /**
     * 在工作线程中执行指定的任务，支持延迟执行
     *
     * @param task 要执行的任务
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    public synchronized void runOnWorkThread(Runnable task, long delayMillis) {
        if (task == null) {
            return;
        }
        long threadId = TaskBeehive.getApplication().getMainLooper().getThread().getId();
        if (Thread.currentThread().getId() != threadId && Looper.myLooper() == null) {
            Looper.prepare();
        }
        removeCallbacks(task);
        if (delayMillis > 0) {
            postDelayed(task, delayMillis);
        } else if (mWorkerThreadID == Thread.currentThread().getId()) {
            task.run();
        } else {
            post(task);
        }
    }

    /**
     * 设置工作线程消息处理回调接口
     *
     * @param workerHandlerCallback 回调接口
     */
    public void setWorkerHandlerCallback(IWorkerHandlerCallback workerHandlerCallback) {
        mWorkerHandlerCallback = workerHandlerCallback;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        if (mWorkerHandlerCallback != null) {
            mWorkerHandlerCallback.handleWorkerMessage(msg);
        }
    }

    /**
     * 工作线程消息处理回调接口
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
    public interface IWorkerHandlerCallback {

        /**
         * 处理工作线程的消息
         *
         * @param msg 接收到的消息对象
         */
        void handleWorkerMessage(Message msg);
    }
}