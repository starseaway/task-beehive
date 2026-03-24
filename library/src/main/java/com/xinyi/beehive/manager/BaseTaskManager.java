package com.xinyi.beehive.manager;

import com.xinyi.beehive.task.BaseTask;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务管理器
 *
 * @author 新一
 * @date 2025/3/18 15:58
 */
public abstract class BaseTaskManager<T extends BaseTask> {

    /**
     * 任务Map集
     */
    protected final Map<String, T> taskMap;

    /**
     * 构造方法
     */
    public BaseTaskManager() {
        // 初始化任务Map集
        taskMap = new HashMap<>();
        // 初始化任务管理器
        initTaskManager();
    }

    /**
     * 初始化任务管理器
     */
    protected void initTaskManager() { }

    /**
     * 添加任务
     *
     * @param task 任务
     */
    public void addTask(T task) {
        taskMap.put(task.getTaskName(), task);
    }

    /**
     * 移除任务
     *
     * @param taskName 任务名称
     */
    public void removeTask(String taskName) {
        taskMap.remove(taskName);
    }

    /**
     * 获取任务
     *
     * @param taskName 任务名称
     * @return 任务
     */
    public BaseTask getTask(String taskName) {
        return taskMap.get(taskName);
    }

    /**
     * 获取任务Map
     *
     * @return 任务Map
     */
    public Map<String, T> getTaskMap() {
        return taskMap;
    }

    /**
     * 获取任务数量
     *
     * @return 任务数量
     */
    public int getTaskCount() {
        return taskMap.size();
    }

    /**
     * 启动某个任务
     */
    public void startTask(String taskName) {
        T task = taskMap.get(taskName);
        if (task != null) {
            task.startTask();
        }
    }

    /**
     * 启动所有任务
     */
    public void startAllTask() {
        for (T task : taskMap.values()) {
            task.startTask();
        }
    }

    /**
     * 暂停某个任务
     */
    public void pauseTask(String taskName) {
        T task = taskMap.get(taskName);
        if (task != null) {
            task.pauseTask();
        }
    }

    /**
     * 暂停所有任务
     */
    public void pauseAllTask() {
        for (T task : taskMap.values()) {
            task.pauseTask();
        }
    }

    /**
     * 恢复某个任务
     */
    public void resumeTask(String taskName) {
        T task = taskMap.get(taskName);
        if (task != null) {
            task.resumeTask();
        }
    }

    /**
     * 恢复所有任务
     */
    public void resumeAllTask() {
        for (T task : taskMap.values()) {
            task.resumeTask();
        }
    }

    /**
     * 回收某个任务
     */
    public void recycleTask(String taskName) {
        T task = taskMap.get(taskName);
        if (task != null) {
            task.recycleTask();
        }
    }

    /**
     * 回收所有任务
     */
    public void recycleAllTask() {
        for (T task : taskMap.values()) {
            task.recycleTask();
        }
        taskMap.clear();
    }
}