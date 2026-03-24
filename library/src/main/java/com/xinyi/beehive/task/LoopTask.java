package com.xinyi.beehive.task;

/**
 * 通用的循环任务，方便快速创建一个循环任务
 *
 * @author 新一
 * @date 2025/6/3 14:27
 */
public class LoopTask extends BaseLoopTask {

    /**
     * 什么也不做
     */
    public static LoopTask create() {
        return new LoopTask();
    }

    /**
     * 创建一个循环任务
     *
     * @param listener 循环任务监听
     */
    public static LoopTask create(LoopTaskListener listener) {
        return new LoopTask(listener);
    }

    /**
     * 创建一个循环任务
     *
     * @param runnable 循环任务
     */
    public static LoopTask create(Runnable runnable) {
        return new LoopTask(runnable);
    }

    /**
     * 创建一个循环任务
     *
     * @param loopDelay 循环延迟时间
     * @param listener 循环任务监听
     */
    public static LoopTask create(long loopDelay, LoopTaskListener listener) {
        return new LoopTask(loopDelay, listener);
    }

    /**
     * 创建一个循环任务
     *
     * @param loopDelay 循环延迟时间
     * @param runnable 循环任务
     */
    public static LoopTask create(long loopDelay, Runnable runnable) {
        return new LoopTask(loopDelay, runnable);
    }

    /**
     * 什么也不做
     */
    public LoopTask() { }

    /**
     * 默认5秒的循环延迟时间
     *
     * @param listener 循环任务监听
     */
    public LoopTask(LoopTaskListener listener) {
        this(DEFAULT_LOOP_DELAY, listener);
    }

    /**
     * 默认5秒的循环延迟时间
     *
     * @param runnable 循环任务
     */
    public LoopTask(Runnable runnable) {
        this(DEFAULT_LOOP_DELAY, loopDelay -> {
            runnable.run();
            return loopDelay;
        });
    }

    /**
     * 自定义循环延迟时间
     *
     * @param loopDelay 循环延迟时间
     * @param listener 循环任务监听
     */
    public LoopTask(long loopDelay, LoopTaskListener listener) {
        setLoopDelay(loopDelay);
        setLoopTaskListener(listener);
    }

    /**
     * 自定义循环延迟时间
     *
     * @param loopDelay 循环延迟时间
     * @param runnable 循环任务
     */
    public LoopTask(long loopDelay, Runnable runnable) {
        this(loopDelay, oldLoopDelay -> {
            runnable.run();
            return oldLoopDelay;
        });
    }

    @Override
    public String getTaskName() {
        return toString();
    }
}