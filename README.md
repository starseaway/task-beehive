# 🐝 TaskBeehive 工蜂任务台（任务调度框架）

<div align="center">
  <img src="task-beehive-logo.svg" width="500" alt="task-beehive-logo">
</div>

![Version](https://img.shields.io/badge/version-2.1.0-blue)
![License](https://img.shields.io/badge/license-Apache%202.0-green)
![API](https://img.shields.io/badge/API-19%2B-brightgreen)

## 一、模块简介
TaskBeehive 是一个专注 “任务调度控制” 的 Android 框架，主要是为了解决项目开发中各种后台任务的处理问题。

它不只是帮你开线程，而是帮你把任务什么时候执行、按什么顺序执行、执行频率是多少这些问题，一次性理清楚。

在日常开发里，我们经常会遇到这些情况：

- Handler / Thread 到处写，越来越乱
- Coroutine 虽然好用，但对 “调度控制” 不够直接
- 定时任务、循环任务、延迟任务各写一套逻辑
- 想做“防抖”、“时间对齐”这种需求，要自己造轮子

TaskBeehive 的目标很简单：

> 👉 把这些零散的任务处理方式，收敛成一套统一的调度体系

你可以把它当成：
- 一个比 Handler 更清晰的线程工具
- 一个比 Coroutine 更偏 “调度控制” 的补充方案
- 一个可以长期托管后台任务的调度框架

## 二、核心能力

框架目前包含但不限于的任务调度能力有：

- 串行任务（保证顺序）
- 并发任务（线程池执行）
- 延迟任务 / 定时任务
- 循环任务（可控生命周期）
- 时间对齐任务（精确卡时间点执行）
- 防抖任务（高频触发合并）
- 单线程事件循环（可以替代 HandlerThread）

## 三、SDK 适用范围

| 项目         | 要求                 |
|------------|--------------------|
| Min SDK    | 19（Android 4.4）及以上 |
| JVM Target | 1.8                |

## 四、集成方式

### 1. 根据 Gradle 版本或项目配置自行决择在合适的位置添加仓库地址
```groovy
maven {
    // jitpack仓库
    url 'https://jitpack.io' 
}
```

### 2. 在 `build.gradle` (Module 级) 中添加依赖：
```groovy
implementation 'com.github.starseaway:task-beehive:2.1.0'
```

```kotlin
implementation("com.github.starseaway:task-beehive:2.1.0")
```

### 3. 初始化

在 `Application` 中完成初始化

```kotlin
class AppApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化工蜂任务台（任务调度框架）
        TaskBeehive.init(this)
    }
}
```

---

## 五、快速上手

以下只是常见的功能示例，更多使用方法可以自行探索。

### 1.全局快捷调度

`TaskBeehive` 提供了一系列静态方法，在一些全局组件的场景中，快速处理常见的线程切换需求。

```kotlin
// 1. 在 UI 线程执行
TaskBeehive.runOnUi {
    Toast.makeText(context, "Hello TaskBeehive", Toast.LENGTH_SHORT).show()
}

// 2. 在全局 Worker 线程执行（串行执行，适合数据库、文件写入等需要保序的操作）
TaskBeehive.runOnWorker {
    // 耗时操作
}

// 3. 在全局线程池中并发执行
TaskBeehive.runAsyncSafe {
    // 并行计算或网络请求
}

// 4. 等等
```

👉 这一层就是“开箱即用”，不用想太多。

---

## 六、核心调度能力

### 1. 周期性后台任务

- 适用于需要长期在后台周期性执行任务的场景，[PeriodicTaskScheduler.java](library/src/main/java/com/xinyi/beehive/core/PeriodicTaskScheduler.java) 支持管理多个不同周期的任务。

```agsl
val scheduler = TaskBeehive.getPeriodicScheduler()

// 创建一个每 5 秒执行一次的任务
val taskId = scheduler.createTask(5000) {
    // 任务内容
}

// 启动任务
scheduler.startTask(taskId)

// 停止任务（不会移除）
// scheduler.stopTask(taskId)

// 移除并停止任务
// scheduler.removeTask(taskId)
```

 - 再精细一点的可控循环任务，一般也可以通过 [LoopTask.java](library/src/main/java/com/xinyi/beehive/task/LoopTask.java) 快捷创建

```kotlin
/**
 * 测试循环任务
 * 
 * @author 新一
 * @date 2025/3/31 11:19
 */
class TestLoopTask : BaseLoopTask(), LoopTask.LoopTaskListener {
    
    init {
        setLoopTaskListener(this)
    }
    
    override fun getTaskName(): String {
        return "任务名称"
    }

    override fun getLoopDelay(): Long {
        // 这里设置任务执行的时间间隔
        
        // 默认5000毫秒
        return super.getLoopDelay()
    }

    override fun setLoopDelay(loopDelay: Long) {
        // 外部也可以设置任务执行的时间间隔
        super.setLoopDelay(loopDelay)
    }
    
    // 循环任务的启动、暂停、恢复、销毁等操作都在基类里实现了，这个类只需要关注任务执行的逻辑即可

    override fun onLoopTask(loopDelay: Long): Long {
        // 任务执行
        
        // 这里可以返回下次执行的时间间隔
        return loopDelay
    }
}
```

更强大可控的循环任务类，可以查看 [ControllableLoopTask.java](library/src/main/java/com/xinyi/beehive/task/ControllableLoopTask.java) 类源码

### 2. 执行精准时间对齐整任务

- 精准定时任务是什么？

精准定时任务是指任务在指定的时间倍数上执行，例如设置了每 5 秒执行一次，那么任务会在 5 秒、10 秒、15 秒、20 秒等时间点执行。

即便是在某分的 04 秒开始启动任务，也会等到 05 秒时执行，绝不会在 04 秒或 09 秒时执行。

这个任务一共支持秒级，分级，时级，天级的控时规则。

```kotlin
/**
 * 测试精准定时任务
 *
 * 本任务支持秒级，分级，时级，天级的控时规则。具体请算法参考源码 [com.xinyi.beehive.algo.TimeAlignmentAlgo] 类。
 *
 * @author 新一
 * @date 2025/3/31 11:24
 */
class TestPreciseTimerTask : PreciseTimerTask(), PreciseTimerTask.OnTimerTaskListener {

    init {
        setOnTimerTaskListener(this)
    }

    override fun getTaskName(): String {
        return "精准定时任务"
    }

    override fun getIntervalInSeconds(): Int {
        // 这里设置任务执行的时间间隔

        // 默认5秒
        return super.getIntervalInSeconds()
    }

    override fun setIntervalInSeconds(intervalInSeconds: Int) {
        // 外部也可以设置任务执行的时间间隔
        super.setIntervalInSeconds(intervalInSeconds)
    }

    // 精准定时任务的启动、暂停、恢复、销毁等操作都在基类里实现了，这个类只需要关注任务执行的逻辑即可

    override fun onTask(scheduledTime: Long, actualTime: Long): Int {
        // 任务执行

        // 这里可以返回下次执行的时间间隔
        return intervalInSeconds
    }
}
```

### 3. 任务管理器

根据实际需决定，你可以创建一个任务管理器，用于管理多个任务的初始化、启动、暂停、恢复、销毁等操作。

```kotlin
/**
 * 循环任务管理器
 * 
 * @author 新一
 * @date 2025/3/31 11:35
 */
class LoopTaskManager : BaseTaskManager<LoopTask>() {

    private val mTestLoopTask by lazy { TestLoopTask() }
    
    override fun initTaskManager() {
        super.initTaskManager()
        
        // 添加循环任务
        addTask(mTestLoopTask)
        
        // 删除循环任务
        removeTask(mTestLoopTask.taskName)
    }
    
    // 基类里已经实现了单个或多个任务的启动、暂停、恢复、销毁等操作
    
    // 该类只需要关注任务的初始化即可，也可以自己定义一些其他的操作
}
```

### 4. 延迟任务

支持通过 CancelToken 随时取消，并可配置执行线程（Dispatcher）

```kotlin
val delayScheduler = TaskBeehive.createDelayScheduler(1, "Demo")

// 提交一个 2 秒后的延迟任务
val token = delayScheduler.schedule(2000) {
    Log.d("Beehive", "延迟任务已触发")
}

// 如果需要提前取消任务
// token.cancel()
```

### 5. 可重置延迟的任务调度器

防抖调度（高频优化神器），源码位置：[ResettableDelayScheduler.java](library/src/main/java/com/xinyi/beehive/core/ResettableDelayScheduler.java)

```kotlin
val resettable = TaskBeehive.createResettableDelayScheduler("Search-Thread")

// 每次调用都会取消上一次未执行的任务并重新计时 500ms
resettable.execute({
    performSearch(keyword)
}, 500)
```

### 6. 时间有序事件循环

内部是单队列、单线程的事件循环组件

> 支持普通任务、紧急任务（抢占当前 tick）和延迟任务，所有任务在单线程中按时间顺序串行执行。

```kotlin
val eventLoop = TaskBeehive.createTimeOrderedEventLoop("Order-Loop")
eventLoop.start()

// 提交不同优先级的任务
eventLoop.postDelayed({ /* 延迟任务 */ }, 1000)
eventLoop.postUrgent { /* 紧急任务 */ }
eventLoop.post { /* 普通任务 */ }
```

### 7. 自定义 [ThreadHandler.java](library/src/main/java/com/xinyi/beehive/core/ThreadHandler.java) 使用案例

如果你需要自定义的线程处理器，只需要实现`ThreadHandlerProxy`接口即可。

```kotlin
/**
 * 比如我要写一个后台日志记录器
 * 
 * @author 新一
 * @date 2025/3/31 13:33
 */
class TestLogger : ThreadHandlerProxy, Handler.Callback {
    
    private val mThreadHandler by lazy { 
        ThreadHandler.createHandler(this, TestLogger::class.java.simpleName)
    }

    override fun getThreadHandler(): ThreadHandler {
        // ThreadHandler 是一个抽象类，提供了默认的实现，也可以继承它自定义实现
        return mThreadHandler
    }
    
    fun log(msg: String) {
        // 记录日志
        LogUtil.d(msg)
        
        // 写入本地文件，是一个耗时操作
        sendWorkThreadMessage(Message.obtain().apply { 
            // 这里可以传递任何数据
            obj = msg
            // 再记录一个产生日志的时间
            arg1 = (System.currentTimeMillis() / 1000).toInt()
        })
    }

    /**
     * 用 Handler 的好处是日志的写入是队列执行的，不会出现顺序错乱的情况
     * 
     * Handler 内置 MessageQueue，保证了消息的有序性，及其适合有序性要求高的场景。
     */
    override fun handleMessage(msg: Message): Boolean {
        // 这里是在工作线程中执行的
        
        // 获取日志信息
        val logMsg = msg.obj as String
        // 获取日志产生时间，单位秒
        val logTime = msg.arg1
        // 写入本地文件
        // ...
        return false
    }
}
```

---

## 七、核心设计（重要）

TaskBeehive 并不是一堆工具类拼在一起，而是分层设计：

```
快捷调度层（runOnUi / runOnWorker / runAsyncSafe）
        ↓
调度器层（Delay / Periodic / Resettable）
        ↓
任务层（LoopTask / PreciseTimerTask）
        ↓
事件循环层（TimeOrderedEventLoop）
```

从“随手用”到“构建系统”，逐层提升，类似渐进式体验。

---

## 八、与 Kotlin Coroutine 的关系

| 能力        | TaskBeehive | Coroutine     |
|-----------|-------------|---------------|
| 线程切换      | ✅           | ✅             |
| 多步骤异步任务编排 | ⚠️（需手动组织）   | ✅             |
| 精准时间调度    | ✅           | ❌             |
| 循环任务控制    | ✅           | ⚠️            |
| 防抖调度      | ✅           | ⚠️（Flow 实现复杂） |
| 时间有序的事件循环 | ✅           | ❌             |

👉 一句话：

协程更适合描述“多个异步步骤之间的依赖关系”，而 TaskBeehive 更擅长控制“任务的执行时机、顺序和调度策略”。

> 一个偏“流程”，一个偏“调度”，如果两者使用得当，就是非常好互补关系

---

## 九、版本变更记录

### V2.1.0 (2026-08-06)

- ✨ feat: 快捷循环任务支持自定义命名，便于调试与任务管理识别
- ✨ feat: 可控循环任务的立即执行可感知是否真正跑完核心逻辑
- 🐞 fix: 修复多线程投递、防抖重排、全局 Handler 初始化等并发正确性问题
- 🐞 fix: 修复循环 / 精准定时任务暂停后仍空转续期，以及可控循环生命周期状态被遮蔽的问题
- 🐞 fix: 修复工作循环自线程暂停死锁，以及工作 Handler 对 Application 的错误依赖
- 🚀 perf: 可控循环改为非阻塞调度，避免长时间占死工作线程
- 🦄 refactor: 精准定时改用更稳妥的调度后端；去掉 Handler 门面多余同步锁
- ♻️ cleanup: 强化任务状态跨线程可见性与任务管理器并发安全
- 🧷 api: 扩展循环任务创建方式，并明确循环回调返回值的调度语义
- 📃 docs: 同步 README 示例与版本记录格式

### V2.0.1 (2026-03-31)
- 🔧 build: 修改 agp 构建版本

### V2.0.0 (2026-03-20)
- ✨ feat: 新增核心任务调度器组件，目录层级调整，重大更新
- ✨ feat: 正式在 Github 开源发布

### V1.2.0 (2025-08-14)
- ✨ feat: 工具类中新增更多快捷调用方法

### V1.1.1 (2025-06-03)
- 🚀 perf: 优化循环任务回调后的间隔时间刷新机制

### V1.1.0 (2025-06-03)
- ✨ feat: 新增用于快速创建循环任务的通用任务类
- ✨ feat: 线程处理器封装判断工作线程是否存活的方法

### V1.0.3 (2025-04-30)
- ✨ feat: 新增超时运行任务能力（超时后不中断，转为后台继续执行）

### V1.0.2 (2025-04-09)
- 🐞 fix: 使用静态内部类替代匿名类，修复 R8 编译阶段 NullPointerException

### V1.0.0 (2025-03-31)
- ✨ feat: 初始化发布，包含串行 / 异步调度、循环任务与精准定时任务等基础能力