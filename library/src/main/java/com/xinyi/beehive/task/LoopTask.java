package com.xinyi.beehive.task;

/**
 * 通用的循环任务，方便快速创建一个循环任务
 *
 * @author 新一
 * @date 2025/6/3 14:27
 */
public class LoopTask extends BaseLoopTask {

    /**
     * 任务名称（同时作为工作线程名）
     */
    private String mTaskName;

    /**
     * 什么也不做
     */
    public static LoopTask create() {
        return new LoopTask();
    }

    /**
     * 创建一个循环任务
     *
     * @param taskName 任务名称
     */
    public static LoopTask create(String taskName) {
        return new LoopTask(taskName);
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
     * @param taskName 任务名称
     * @param listener 循环任务监听
     */
    public static LoopTask create(String taskName, LoopTaskListener listener) {
        return new LoopTask(taskName, listener);
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
     * @param taskName 任务名称
     * @param runnable 循环任务
     */
    public static LoopTask create(String taskName, Runnable runnable) {
        return new LoopTask(taskName, runnable);
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
     * @param taskName 任务名称
     * @param loopDelay 循环延迟时间
     * @param listener 循环任务监听
     */
    public static LoopTask create(String taskName, long loopDelay, LoopTaskListener listener) {
        return new LoopTask(taskName, loopDelay, listener);
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
     * 创建一个循环任务
     *
     * @param taskName 任务名称
     * @param loopDelay 循环延迟时间
     * @param runnable 循环任务
     */
    public static LoopTask create(String taskName, long loopDelay, Runnable runnable) {
        return new LoopTask(taskName, loopDelay, runnable);
    }

    /**
     * 什么也不做
     */
    public LoopTask() { }

    /**
     * @param taskName 任务名称
     */
    public LoopTask(String taskName) {
        setTaskName(taskName);
    }

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
     * @param taskName 任务名称
     * @param listener 循环任务监听
     */
    public LoopTask(String taskName, LoopTaskListener listener) {
        this(taskName, DEFAULT_LOOP_DELAY, listener);
    }

    /**
     * 默认5秒的循环延迟时间
     *
     * @param runnable 循环任务
     */
    public LoopTask(Runnable runnable) {
        this(DEFAULT_LOOP_DELAY, runnable);
    }

    /**
     * 默认5秒的循环延迟时间
     *
     * @param taskName 任务名称
     * @param runnable 循环任务
     */
    public LoopTask(String taskName, Runnable runnable) {
        this(taskName, DEFAULT_LOOP_DELAY, runnable);
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
     * @param taskName 任务名称
     * @param loopDelay 循环延迟时间
     * @param listener 循环任务监听
     */
    public LoopTask(String taskName, long loopDelay, LoopTaskListener listener) {
        setTaskName(taskName);
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

    /**
     * 自定义循环延迟时间
     *
     * @param taskName 任务名称
     * @param loopDelay 循环延迟时间
     * @param runnable 循环任务
     */
    public LoopTask(String taskName, long loopDelay, Runnable runnable) {
        this(taskName, loopDelay, oldLoopDelay -> {
            runnable.run();
            return oldLoopDelay;
        });
    }

    /**
     * 设置任务名称（同时作为工作线程名，需在 {@link #startTask()} 前调用）
     *
     * @param taskName 任务名称
     */
    public void setTaskName(String taskName) {
        this.mTaskName = taskName;
    }

    @Override
    public String getTaskName() {
        if (mTaskName == null || mTaskName.isEmpty()) {
            return toString();
        }
        return mTaskName;
    }
}