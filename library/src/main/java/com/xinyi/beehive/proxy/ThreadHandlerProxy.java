package com.xinyi.beehive.proxy;

import android.os.Message;

import androidx.annotation.Nullable;

import com.xinyi.beehive.strategy.ThreadHandlerStrategy;
import com.xinyi.beehive.core.ThreadHandler;
import com.xinyi.beehive.core.UiHandler;
import com.xinyi.beehive.core.WorkerHandler;

/**
 * 线程处理代理（Proxy）
 *
 * <p>
 *   用于在 UI 线程或工作线程中执行任务，
 *   无需直接管理线程逻辑，而是通过代理进行任务调度。
 * </p>
 *
 * <p>
 *   该代理层不仅封装了 ThreadHandler 的调用，还能够适配不同的 ThreadHandler 实现，
 *   因此本质上它既是一个策略适配器，但也算是ThreadHandler的代理。
 * </p>
 *
 * @author 新一
 * @date 2025/3/18 14:12
 */
public interface ThreadHandlerProxy extends ThreadHandlerStrategy {

    /**
     * 获取工作线程上处理的 Handler
     *
     * @return ThreadHandler 实例
     */
    @Nullable
    ThreadHandler getThreadHandler();

    /**
     * 获取Ui线程的Handler对象
     */
    @Nullable
    @Override
    default UiHandler getUiHandler() {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            return handler.getUiHandler();
        }
        return null;
    }

    /**
     * 在主线程上运行任务
     *
     * @param task 要执行的任务
     */
    @Override
    default void runOnUiThreadTask(Runnable task) {
        if (task == null) {
            return;
        }
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.runOnUiThreadTask(task);
        }
    }

    /**
     * 在主线程上运行任务，支持延迟执行
     *
     * @param task 要执行的任务
     * @param duration 延迟执行的时间（毫秒）
     */
    @Override
    default void runOnUiThreadTask(Runnable task, long duration) {
        if (task == null) {
            return;
        }
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.runOnUiThreadTask(task, duration);
        }
    }

    /**
     * 从主线程中移除指定的任务
     *
     * @param task 要移除的任务
     */
    @Override
    default void removeFromUiThread(Runnable task) {
        if (task == null) {
            return;
        }
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.removeFromUiThread(task);
        }
    }

    /**
     * 发送消息到 UI 线程的消息队列中
     *
     * @param msg 要发送的消息对象
     */
    @Override
    default void sendUiThreadMessage(Message msg) {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.sendUiThreadMessage(msg);
        }
    }

    /**
     * 发送延迟消息到 UI 线程的消息队列中
     *
     * @param msg 要发送的消息对象
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    @Override
    default void sendUiThreadMessageDelayed(Message msg, long delayMillis) {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.sendUiThreadMessageDelayed(msg, delayMillis);
        }
    }

    /**
     * 获取工作线程的Handler对象
     */
    @Nullable
    @Override
    default WorkerHandler getWorkerHandler() {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            return handler.getWorkerHandler();
        }
        return null;
    }

    /**
     * 在工作线程中执行任务
     *
     * @param task 要添加到队列中的任务
     */
    @Override
    default void runOnWorkThreadTask(Runnable task) {
        if (task == null) {
            return;
        }
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.runOnWorkThreadTask(task);
        }
    }

    /**
     * 在工作线程中延迟执行任务
     *
     * @param task 要添加到队列中的任务
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    @Override
    default void runOnWorkThreadTask(Runnable task, long delayMillis) {
        if (task == null) {
            return;
        }
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.runOnWorkThreadTask(task, delayMillis);
        }
    }

    /**
     * 从工作线程中移除指定的任务
     *
     * @param task 要移除的任务
     */
    @Override
    default void removeFromWorkThread(Runnable task) {
        if (task == null) {
            return;
        }
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.removeFromWorkThread(task);
        }
    }

    /**
     * 发送消息到工作线程的消息队列中
     *
     * @param msg 要发送的消息对象
     */
    @Override
    default void sendWorkThreadMessage(Message msg) {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.sendWorkThreadMessage(msg);
        }
    }

    /**
     * 发送延迟消息到工作线程的消息队列中
     *
     * @param msg 要发送的消息对象
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    @Override
    default void sendWorkThreadMessageDelayed(Message msg, long delayMillis) {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.sendWorkThreadMessageDelayed(msg, delayMillis);
        }
    }

    /**
     * 移除工作线程中消息队列中的所有任务和消息
     */
    @Override
    default void removeWorkCallbacksAndMessages() {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.removeWorkCallbacksAndMessages();
        }
    }

    /**
     * 判断工作线程处理器是否存活
     */
    @Override
    default boolean isWorkerThreadAlive() {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            return handler.isWorkerThreadAlive();
        }
        return false;
    }

    /**
     * 退出线程处理器并释放资源
     */
    @Override
    default void quitSafely() {
        ThreadHandler handler = getThreadHandler();
        if (handler != null) {
            handler.quitSafely();
        }
    }
}