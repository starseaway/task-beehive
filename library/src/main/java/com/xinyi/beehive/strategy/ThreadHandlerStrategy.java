package com.xinyi.beehive.strategy;

import android.os.Message;

import com.xinyi.beehive.core.UiHandler;
import com.xinyi.beehive.core.WorkerHandler;

/**
 * 线程处理器的策略接口
 *
 * @author 新一
 * @date 2025/3/18 14:20
 */
public interface ThreadHandlerStrategy {

    /**
     * 获取Ui线程的Handler对象
     */
    UiHandler getUiHandler();

    /**
     * 在主线程上运行任务
     *
     * @param task 要执行的任务
     */
    default void runOnUiThreadTask(Runnable task) { }

    /**
     * 在主线程上运行任务，支持延迟执行
     *
     * @param task 要执行的任务
     * @param duration 延迟执行的时间（毫秒）
     */
    default void runOnUiThreadTask(Runnable task, long duration) { }

    /**
     * 从主线程中移除指定的任务
     *
     * @param task 要移除的任务
     */
    default void removeFromUiThread(Runnable task) { }

    /**
     * 发送消息到 UI 线程的消息队列中
     *
     * @param msg 要发送的消息对象
     */
    default void sendUiThreadMessage(Message msg) { }

    /**
     * 发送延迟消息到 UI 线程的消息队列中
     *
     * @param msg 要发送的消息对象
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    default void sendUiThreadMessageDelayed(Message msg, long delayMillis) { }

    /**
     * 获取工作线程的Handler对象
     */
    WorkerHandler getWorkerHandler();

    /**
     * 在工作线程中执行任务
     *
     * @param task 要添加到队列中的任务
     */
    default void runOnWorkThreadTask(Runnable task) { }

    /**
     * 在工作线程中延迟执行任务
     *
     * @param task 要添加到队列中的任务
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    default void runOnWorkThreadTask(Runnable task, long delayMillis) { }

    /**
     * 从工作线程中移除指定的任务
     *
     * @param task 要移除的任务
     */
    default void removeFromWorkThread(Runnable task) { }

    /**
     * 发送消息到工作线程的消息队列中
     *
     * @param msg 要发送的消息对象
     */
    default void sendWorkThreadMessage(Message msg) { }

    /**
     * 发送延迟消息到工作线程的消息队列中
     *
     * @param msg 要发送的消息对象
     * @param delayMillis 延迟执行的时间（毫秒）
     */
    default void sendWorkThreadMessageDelayed(Message msg, long delayMillis) { }

    /**
     * 移除工作线程中消息队列中的所有任务和消息
     */
    default void removeWorkCallbacksAndMessages() { }

    /**
     * 判断工作线程处理器是否存活
     */
    default boolean isWorkerThreadAlive() {
        return false;
    }

    /**
     * 退出线程处理器并释放资源
     */
    default void quitSafely() { }
}