package com.xinyi.beehive.core;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Printer;

import com.xinyi.beehive.strategy.ThreadHandlerStrategy;

/**
 * 线程处理程器，最低可支持Android 4.4
 *
 * @author 新一
 * @date 2025/3/18 11:03
 */
public abstract class ThreadHandler implements ThreadHandlerStrategy {

    private static final String TAG = ThreadHandler.class.getSimpleName();

    /**
     * 创建一个默认的ThreadHandler线程处理器对象
     */
    public static ThreadHandler createHandler() {
        return createHandler(TAG);
    }

    /**
     * 创建一个默认的ThreadHandler线程处理器对象
     *
     * @param threadName 线程名
     */
    public static ThreadHandler createHandler(String threadName) {
        return createHandler(null, threadName);
    }

    /**
     * 创建一个默认的ThreadHandler线程处理器对象
     *
     * @param callback 回调对象
     * @param threadName 线程名
     */
    public static ThreadHandler createHandler(Handler.Callback callback, String threadName) {
        return createHandler(callback, null, threadName);
    }

    /**
     * 创建一个默认的ThreadHandler线程处理器对象
     *
     * @param callback 回调对象
     * @param printer 打印机对象，用于记录日志
     * @param threadName 线程名
     */
    public static ThreadHandler createHandler(Handler.Callback callback, Printer printer, String threadName) {
        return new ThreadHandler() {

            @Override
            protected String getThreadHandlerName() {
                return threadName;
            }

            /**
             * 获取工作线程的Handler对象
             */
            @Override
            public WorkerHandler getWorkerHandler() {
                if (mWorkerHandler == null) {
                    synchronized (ThreadHandler.class) {
                        if (mWorkerHandler == null) {
                            mWorkerHandler = WorkerHandler.createHandler(callback, printer, threadName);
                        }
                    }
                }
                return mWorkerHandler;
            }
        };
    }

    /**
     * 狗仔函数
     */
    public ThreadHandler() {
        getUiHandler();
        getWorkerHandler();
    }

    /**
     * UI 线程的 Handler
     */
    protected volatile UiHandler mUIHandler;

    /**
     * 工作线程的 Handler
     */
    protected volatile WorkerHandler mWorkerHandler;

    /**
     * 获取线程处理器的名称，就是工作线程的 Handler 线程命名
     */
    protected String getThreadHandlerName() {
        return TAG;
    }

    /**
     * 获取Ui线程的Handler对象
     */
    @Override
    public UiHandler getUiHandler() {
        if (mUIHandler == null) {
            synchronized (ThreadHandler.class) {
                if (mUIHandler == null) {
                    mUIHandler = UiHandler.createHandler();
                }
            }
        }
        return mUIHandler;
    }

    /**
     * 在 UI 线程中运行指定的任务
     *
     * @param task 要执行的任务
     */
    @Override
    public synchronized void runOnUiThreadTask(Runnable task) {
        runOnUiThreadTask(task, 0L);
    }

    /**
     * 在 UI 线程中运行指定的任务，支持延迟执行
     *
     * @param task     要执行的任务
     * @param duration 延迟执行的时间（毫秒）
     */
    @Override
    public synchronized void runOnUiThreadTask(Runnable task, long duration) {
        try {
            getUiHandler().runOnUiThread(task, duration);
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "在Ui线程执行失败", exception);
        }
    }

    /**
     * 移除指定的任务，防止其执行
     *
     * @param task 要移除的任务
     */
    @Override
    public synchronized void removeFromUiThread(Runnable task) {
        getUiHandler().removeCallbacks(task);
    }

    /**
     * 获取工作线程的Handler对象
     */
    @Override
    public WorkerHandler getWorkerHandler() {
        if (mWorkerHandler == null) {
            synchronized (ThreadHandler.class) {
                if (mWorkerHandler == null) {
                    mWorkerHandler = WorkerHandler.createHandler(getThreadHandlerName());
                }
            }
        }
        return mWorkerHandler;
    }

    /**
     * 在工作线程中执行指定的任务
     *
     * @param task 要执行的任务
     */
    @Override
    public synchronized void runOnWorkThreadTask(Runnable task) {
        runOnWorkThreadTask(task, 0L);
    }

    /**
     * 在工作线程中执行指定的任务，支持延迟执行
     *
     * @param task        要执行的任务
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    @Override
    public synchronized void runOnWorkThreadTask(Runnable task, long delayMillis) {
        try {
            getWorkerHandler().runOnWorkThread(task, delayMillis);
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "在工作线程执行失败", exception);
        }
    }

    /**
     * 移除工作线程中的指定任务
     *
     * @param task 要移除的任务
     */
    @Override
    public synchronized void removeFromWorkThread(Runnable task) {
        try {
            getWorkerHandler().removeCallbacks(task);
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "从队列异常中删除任务失败", exception);
        }
    }

    /**
     * 移除工作线程中消息队列中的所有任务和消息
     */
    @Override
    public synchronized void removeWorkCallbacksAndMessages() {
        try {
            getWorkerHandler().removeCallbacksAndMessages(null);
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "删除回调和消息异常", exception);
        }
    }

    /**
     * 发送消息到工作线程的消息队列
     *
     * @param msg 要发送的消息对象
     */
    @Override
    public synchronized void sendWorkThreadMessage(Message msg) {
        sendWorkThreadMessageDelayed(msg, 0L);
    }

    /**
     * 发送延迟消息到工作线程的消息队列
     *
     * @param msg 要发送的消息对象
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    @Override
    public synchronized void sendWorkThreadMessageDelayed(Message msg, long delayMillis) {
        try {
            getWorkerHandler().sendMessageDelayed(msg, delayMillis);
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "发送工作线程消息异常", exception);
        }
    }

    /**
     * 发送消息到 UI 线程的消息队列
     *
     * @param msg 要发送的消息对象
     */
    @Override
    public synchronized void sendUiThreadMessage(Message msg) {
        sendUiThreadMessageDelayed(msg, 0L);
    }

    /**
     * 发送延迟消息到 UI 线程的消息队列
     *
     * @param msg         要发送的消息对象
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    @Override
    public synchronized void sendUiThreadMessageDelayed(Message msg, long delayMillis) {
        try {
            mUIHandler.sendMessageDelayed(msg, delayMillis);
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "发送Ui线程消息异常", exception);
        }
    }

    /**
     * 设置 UI 线程消息处理回调接口
     *
     * @param callback 回调接口
     */
    public void setUiHandlerCallback(UiHandler.IUiHandlerCallback callback) {
        getUiHandler().setUiHandlerCallback(callback);
    }

    /**
     * 设置工作线程消息处理回调接口
     *
     * @param callback 回调接口
     */
    public void setWorkerHandlerCallback(WorkerHandler.IWorkerHandlerCallback callback) {
        getWorkerHandler().setWorkerHandlerCallback(callback);
    }

    /**
     * 判断工作线程处理器是否存活
     */
    @Override
    public boolean isWorkerThreadAlive() {
        return getWorkerHandler().getLooper().getThread().isAlive();
    }

    /**
     * 退出线程处理器并释放资源
     */
    @Override
    public synchronized void quitSafely() {
        try {
            getWorkerHandler().getLooper().quitSafely();
            removeWorkCallbacksAndMessages();
            mWorkerHandler = null;
            mUIHandler = null;
        } catch (Exception exception) {
            Log.e(getThreadHandlerName(), "退出线程处理器异常", exception);
        }
    }
}