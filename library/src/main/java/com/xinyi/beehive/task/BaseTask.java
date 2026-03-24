package com.xinyi.beehive.task;

/**
 * 所有自定义任务的基类
 *
 * @author 新一
 * @date 2025/3/18 15:17
 */
public abstract class BaseTask {

    /**
     * 任务名称
     */
    public abstract String getTaskName();

    /**
     * 任务运行状态
     */
    protected boolean isRunning = false;

    /**
     * 任务暂停状态
     */
    protected boolean isPaused = false;

    /**
     * 任务体
     */
    public abstract void runTask();

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 是否已经暂停
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * 启动任务
     */
    public void startTask() {
        isRunning = true;
    }

    /**
     * 暂停任务
     */
    public void pauseTask() {
        isPaused = true;
    }

    /**
     * 恢复任务
     */
    public void resumeTask() {
        if (!isPaused) {
            return;
        }
        isPaused = false;
    }

    /**
     * 回收任务
     */
    public void recycleTask() {
        isRunning = false;
        isPaused = false;
    }
}