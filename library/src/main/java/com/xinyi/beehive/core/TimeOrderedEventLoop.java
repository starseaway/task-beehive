package com.xinyi.beehive.core;

import android.os.SystemClock;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 时间有序单队列单线程事件循环
 *
 * <p> 所有任务在同一线程串行执行，多线程可安全 post 任务 </p>
 *
 * 支持：【单线程串行执行所有任务、执行延时 / 紧急 / 普通任务】
 *
 * @author 新一
 * @date 2026/2/9 9:12
 */
public final class TimeOrderedEventLoop extends WorkerLoop {

    /**
     * 多生产者入口队列（线程安全）
     */
    private final Queue<Node> ingressQueue = new ConcurrentLinkedQueue<>();

    /**
     * 工作线程名称
     */
    private final String threadName;

    /**
     * 时间轴最小堆（按 when + seq 排序）
     */
    private Node[] heap = new Node[32];

    /**
     * 当前堆大小
     */
    private int heapSize;

    /**
     * 递增序列号（保证 FIFO 稳定性）
     */
    private long sequence;

    /**
     * 构造函数
     *
     * @param threadName 线程名称
     */
    public TimeOrderedEventLoop(String threadName) {
        this.threadName = threadName;
    }

    @Override
    protected String threadName() {
        return threadName;
    }

    /**
     * 核心工作循环
     */
    @Override
    protected void doWorkLoop() {
        final long now = SystemClock.uptimeMillis();

        // 吸收所有新提交的任务
        ingestPendingTasks();

        // 执行所有已到期的任务
        for (;;) {
            Node node = peek();
            if (node == null || node.when > now) {
                break;
            }
            runSafely(poll());
        }

        // 计算下一次唤醒时间
        Node next = peek();
        if (next == null) {
            // 没有任何任务，线程进入无限期休眠
            parkLoop();
            return;
        }

        long delay = next.when - now;
        if (delay > 0) {
            // 有未来任务，精确休眠到最近时间点
            parkLoopNanos(delay * 1_000_000L);
        }
    }

    /**
     * 安全执行任务，避免异常破坏 loop
     */
    private void runSafely(Node node) {
        try {
            node.task.run();
        } catch (Throwable throwable) {
            onLoopException(throwable);
        }
    }

    /**
     * 吸收入口队列中的所有任务到时间轴
     */
    private void ingestPendingTasks() {
        Node node;
        while ((node = ingressQueue.poll()) != null) {
            heapOffer(node);
        }
    }

    /**
     * 向时间轴最小堆中插入一个节点
     *
     * <p>
     *   堆结构说明：
     *   <ul>
     *     <li>采用二叉最小堆实现时间排序</li>
     *     <li>堆顶元素（index=0）永远是最早需要执行的任务</li>
     *     <li>插入复杂度：O(log n)</li>
     *   </ul>
     * </p>
     *
     * <p>
     *   上浮（sift-up）策略：
     *   <pre>
     *     1. 新节点先放到数组尾部
     *     2. 与父节点比较，如果更小则交换
     *     3. 持续向上直到满足堆序
     *   </pre>
     * </p>
     */
    private void heapOffer(Node node) {
        int i = heapSize;

        // 容量不足时倍增
        if (i >= heap.length) {
            heap = Arrays.copyOf(heap, heap.length << 1);
        }

        heapSize = i + 1;

        // 向上调整（保持最小堆性质）
        while (i > 0) {
            int parentIndex = (i - 1) >>> 1; // 父节点 index
            Node parent = heap[parentIndex];

            // 如果父节点已经 <= 当前节点，说明堆序满足
            if (compare(parent, node) <= 0) {
                break;
            }

            // 父节点下沉
            heap[i] = parent;
            i = parentIndex;
        }

        // 放入最终位置
        heap[i] = node;
    }

    /**
     * 查看当前最早需要执行的任务（不移除）
     *
     * @return 堆顶节点，若为空返回 null
     */
    private Node peek() {
        return heapSize == 0 ? null : heap[0];
    }

    /**
     * 取出并移除最早执行的任务
     *
     * @return 被移除的堆顶节点
     */
    private Node poll() {
        int lastIndex = --heapSize;

        // 当前堆顶
        Node result = heap[0];

        // 将堆尾元素取出，准备下沉到合适位置
        Node lastNode = heap[lastIndex];
        heap[lastIndex] = null;

        // 若删除后堆中仍有元素，需要恢复最小堆结构
        if (lastIndex != 0) {
            siftDown(0, lastNode);
        }
        return result;
    }

    /**
     * 堆下沉操作（sift-down）
     *
     * <p> poll() 后恢复最小堆结构 </p>
     *
     * <p>
     *   算法说明：
     *   <pre>
     *      node 从 index 开始向下移动：
     *      - 找到两个子节点中更小的那个
     *      - 若子节点更小，则交换
     *      - 持续直到满足堆序
     *   </pre>
     * </p>
     *
     * @param index 起始位置
     * @param node 需要下沉的节点
     */
    private void siftDown(int index, Node node) {
        // 非叶子节点的上界（超过 half 就是叶子）
        int half = heapSize >>> 1;

        while (index < half) {
            // 左子节点
            int childIndex = (index << 1) + 1;
            Node child = heap[childIndex];

            // 右子节点存在时，选择更小的那个
            int right = childIndex + 1;
            if (right < heapSize && compare(child, heap[right]) > 0) {
                child = heap[childIndex = right];
            }

            // 当前 node 已经比子节点小，说明位置正确
            if (compare(node, child) <= 0) {
                break;
            }

            // 子节点上浮
            heap[index] = child;
            index = childIndex;
        }

        // 放入最终位置
        heap[index] = node;
    }

    /**
     * 排序规则：
     *
     * <ul>
     *   <li>when 越小，优先级越高</li>
     *   <li>when 相同，按 seq FIFO</li>
     * </ul>
     */
    private int compare(Node a, Node b) {
        if (a.when < b.when) return -1;
        if (a.when > b.when) return 1;
        return Long.compare(a.seq, b.seq);
    }

    /**
     * 提交普通任务（FIFO）
     */
    public void post(Runnable task) {
        if (task == null || isStopped()) {
            return;
        }
        enqueue(task, SystemClock.uptimeMillis());
    }

    /**
     * 提交紧急任务（仅抢占当前 tick，不会饿死延时任务）
     */
    public void postUrgent(Runnable task) {
        if (task == null || isStopped()) {
            return;
        }
        enqueue(task, SystemClock.uptimeMillis() - 1);
    }

    /**
     * 提交延迟任务
     *
     * @param task 任务
     * @param delayMillis 延迟时间（ms）
     */
    public void postDelayed(Runnable task, long delayMillis) {
        if (task == null || isStopped()) {
            return;
        }
        long when = SystemClock.uptimeMillis() + Math.max(0, delayMillis);
        enqueue(task, when);
    }

    /**
     * 统一入队逻辑
     */
    private void enqueue(Runnable task, long when) {
        Node node = new Node(task, when, sequence++);
        ingressQueue.offer(node);

        // 精确唤醒工作线程
        unparkLoop();
    }

    /**
     * 线程退出前清理资源
     */
    @Override
    protected void onLoopExit() {
        ingressQueue.clear();
        Arrays.fill(heap, null);
        heapSize = 0;
    }

    /**
     * 时间轴节点
     */
    private static final class Node {

        /**
         * 实际执行任务
         */
        final Runnable task;

        /**
         * 执行时间点（必须基于 uptimeMillis）
         */
        final long when;

        /**
         * FIFO 序列号
         */
        final long seq;

        Node(Runnable task, long when, long seq) {
            this.task = task;
            this.when = when;
            this.seq = seq;
        }
    }
}