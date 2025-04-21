# Java Scheduler Service Documentation

## Overview

This document provides a detailed explanation of the `SchedulerService` implementation, a custom task scheduling system built with Java's low-level concurrency primitives. The scheduler allows tasks to be scheduled for one-time execution, recurring execution, or recurring execution where each iteration waits for the previous one to complete.

## Components

### Core Classes

1. **SchedulerService**: The main service class that manages the scheduling and execution of tasks.
2. **Task**: A data class that wraps a `Runnable` task with scheduling metadata.

### Key Dependencies

- `PriorityQueue`: Orders tasks by their scheduled execution time
- `ThreadPoolExecutor`: Executes tasks using a pool of worker threads
- `ReentrantLock` & `Condition`: Provide thread synchronization mechanisms

## SchedulerService Class

### Fields

```java
private final PriorityQueue<Task> taskQueue;    // Stores and orders tasks by execution time
private final ThreadPoolExecutor threadPoolExecutor;  // Executes the actual tasks
private final Lock lock;                        // Synchronizes access to the queue
private final Condition condition;              // Allows efficient waiting and signaling
```

### Constructor

```java
public SchedulerService() {
    this.taskQueue = new PriorityQueue<>(Comparator.comparingLong(Task::getScheduledTime));
    this.threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    this.lock = new ReentrantLock();
    this.condition = this.lock.newCondition();
}
```

The constructor initializes:
- A priority queue that sorts tasks by their scheduled execution time
- A cached thread pool that creates threads as needed and reuses them
- A reentrant lock for thread safety
- A condition variable for efficient waiting and notification

### Main Execution Loop (`run` method)

```java
@Override
public void run() {
    Long timeToSleep = 0l;
    while (true) {
        lock.lock();
        try {
            // Code inside try block...
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
```

The `run` method contains an infinite loop that continuously:
1. Acquires the lock to ensure thread safety
2. Processes tasks that are due for execution
3. Releases the lock in a `finally` block to ensure it's always released

### Empty Queue Handling

```java
while (taskQueue.isEmpty()) {
        this.condition.await();
}
```

When there are no tasks in the queue:
1. The scheduler thread waits indefinitely using `condition.await()`
2. This releases the lock while waiting, allowing other threads to add tasks
3. When another thread adds a task and signals the condition, this thread will wake up

### Waiting for Next Task Execution

```java
while (!taskQueue.isEmpty()) {
timeToSleep = taskQueue.peek().getScheduledTime() - System.currentTimeMillis();
    if (timeToSleep <= 0) {
        break;
        }
        condition.await(timeToSleep, TimeUnit.MILLISECONDS);
}
```

When there are tasks in the queue but the earliest one is not yet due:
1. Calculate how long to wait until the next task is due
2. If the task is already due (timeToSleep ≤ 0), break the loop and execute it
3. Otherwise, wait for the calculated duration or until signaled (whichever comes first)
4. If signaled (because a new task was added), recalculate the wait time

### Task Processing

```java
Task task = taskQueue.poll();  // Remove the earliest task from the queue
Long scheduledTime = 0l;

switch (task.getTaskType()) {
        case RUN_ONCE -> {
        threadPoolExecutor.submit(task.getTask());
        }
        // Other cases...
        }
```

Task execution depends on the task type:

#### One-time Task (`RUN_ONCE`)

```java
case RUN_ONCE -> {
        threadPoolExecutor.submit(task.getTask());
        }
```

For one-time execution:
1. The task is submitted to the thread pool
2. No further action is needed

#### Recurring Task (`RECUR`)

```java
case RECUR -> {
scheduledTime = System.currentTimeMillis() + task.getTimeUnit().toMillis(task.getRecurringTime());
        threadPoolExecutor.submit(task.getTask());
        task.setScheduledTime(scheduledTime);
    taskQueue.add(task);
}
```

For recurring execution without waiting:
1. Calculate the next execution time based on the recurring interval
2. Submit the current instance of the task to the thread pool
3. Update the task with the new scheduled time
4. Add the task back to the queue for future execution

#### Recurring Task with Wait (`RECUR_WITH_WAIT`)

```java
case RECUR_WITH_WAIT -> {
        threadPoolExecutor.submit(() -> {
        try {
        task.getTask().run();

// Reschedule after task completes
            lock.lock();
            try {
long newTime = System.currentTimeMillis() + task.getTimeUnit().toMillis(task.getRecurringTime());
                task.setScheduledTime(newTime);
                taskQueue.add(task);
                condition.signalAll();
            } finally {
                    lock.unlock();
            }
                    } catch (Exception e) {
        e.printStackTrace();
        }
                });
                }
```

For recurring execution with wait:
1. Submit a new lambda to the thread pool that:
    - Executes the original task
    - After completion, acquires the lock
    - Calculates the next execution time
    - Updates the task and adds it back to the queue
    - Signals the scheduler thread that the queue has changed
    - Releases the lock
2. This ensures the next execution is scheduled only after the current execution completes
3. The lock is only held briefly during rescheduling, not during task execution

### Task Scheduling Methods

#### One-time Task

```java
public void scheduleTask(Runnable task, Long delay, TimeUnit timeUnit) {
    addTask(task, delay, null, timeUnit, Task.ScheduledTaskType.RUN_ONCE);
}
```

Schedules a task to run once after the specified delay.

#### Recurring Task

```java
public void scheduleRecurringTask(Runnable task, Long delay, Long recurringTime, TimeUnit timeUnit) {
    addTask(task, delay, recurringTime, timeUnit, Task.ScheduledTaskType.RECUR);
}
```

Schedules a task to run repeatedly:
- First execution occurs after the initial delay
- Subsequent executions occur at regular intervals defined by recurringTime
- Executions can overlap if a task takes longer than its interval

#### Recurring Task with Wait

```java
public void scheduleRecurringTaskWithWait(Runnable task, Long delay, Long recurringTime, TimeUnit timeUnit) {
    addTask(task, delay, recurringTime, timeUnit, Task.ScheduledTaskType.RECUR_WITH_WAIT);
}
```

Schedules a task to run repeatedly with wait:
- First execution occurs after the initial delay
- Subsequent executions occur only after the previous execution completes
- The recurring interval starts after the previous execution completes

### Task Addition Helper Method

```java
private void addTask(Runnable task, Long delay, Long recurringTime, TimeUnit timeUnit, Task.ScheduledTaskType scheduledTaskType) {
    lock.lock();
    try {
        Long scheduledTime = System.currentTimeMillis() + timeUnit.toMillis(delay);
        Task newTask = new Task(task, scheduledTime, delay, recurringTime, scheduledTaskType, timeUnit);
        taskQueue.add(newTask);
        condition.signalAll();
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        lock.unlock();
    }
}
```

This private helper method:
1. Acquires the lock to ensure thread safety
2. Calculates the absolute time when the task should first execute
3. Creates a new Task object with all necessary metadata
4. Adds the task to the priority queue
5. Signals all waiting threads that the queue has changed
6. Releases the lock

## Task Class

```java
@Data
@AllArgsConstructor
public class Task {
    private Runnable task;         // The actual code to execute
    private Long scheduledTime;     // Absolute time when task should execute (in milliseconds)
    private Long delay;             // Initial delay before first execution
    private Long recurringTime;     // Interval for recurring tasks
    private ScheduledTaskType taskType;  // Type of scheduling (one-time, recurring, etc.)
    private TimeUnit timeUnit;      // Time unit for delay and recurring time

    public enum ScheduledTaskType {
        RUN_ONCE, RECUR, RECUR_WITH_WAIT;
    }
}
```

The Task class is a data container that holds:
- The actual runnable task to execute
- Timing information (when to execute, recurring intervals)
- The type of scheduling behavior
- The time unit for interpreting delay and recurring intervals

## Starting the Scheduler

To use the scheduler:

```java
SchedulerService scheduler = new SchedulerService();
Thread schedulerThread = new Thread(scheduler);
schedulerThread.start();

// Schedule tasks
scheduler.scheduleTask(() -> System.out.println("One-time task"), 5L, TimeUnit.SECONDS);
scheduler.scheduleRecurringTask(() -> System.out.println("Recurring task"), 1L, 2L, TimeUnit.SECONDS);
```

## Concurrency Aspects

### Thread Safety

Thread safety is achieved through:
- Lock-based synchronization for all queue operations
- Proper signaling between threads using conditions
- Explicit lock acquisition/release in try-finally blocks

### Lock Usage

The ReentrantLock provides:
- Exclusive access to the task queue
- The ability to create condition variables
- Support for timed waiting (essential for the scheduler)

### Condition Variables

Condition variables enable:
- Efficient waiting until tasks are due
- Immediate wake-up when earlier tasks are added
- Clear signaling between the scheduling and task addition threads

## Limitations and Considerations

1. **No Shutdown Mechanism**: The current implementation runs indefinitely.
2. **Basic Error Handling**: Exceptions are only printed to stderr.
3. **No Task Cancellation**: There's no way to cancel scheduled tasks.
4. **Memory Usage**: All tasks remain in memory, which could be an issue for large numbers of tasks.
5. **No Persistence**: Tasks are lost if the application restarts.

## Thread Model

1. **Main Scheduler Thread**: Continuously checks for due tasks and submits them for execution
2. **Worker Threads**: Execute the actual tasks from the thread pool
3. **Client Threads**: Add tasks to the scheduler from outside

## Concurrency Patterns Used

1. **Producer-Consumer**: Client threads produce tasks, scheduler consumes them
2. **Thread Pool**: Tasks execute in a managed pool of worker threads
3. **Monitor Object**: Lock and condition variables implement the monitor pattern
4. **Priority Scheduling**: Tasks are ordered by execution time

## What If Components Were Missing?

Understanding the importance of each component by examining what would happen if they were removed:

### Without Locks (`ReentrantLock`)

If we removed the locking mechanism:

1. **Race Conditions**: Multiple threads could modify the priority queue simultaneously, leading to:
    - Corrupted queue structure (violating heap property)
    - Lost tasks (if two threads add tasks at once)
    - Duplicate task execution (if two threads check and execute the same task)

2. **Visibility Issues**: Changes made by one thread might not be visible to other threads due to CPU caching or instruction reordering.

3. **No Condition Support**: Condition variables require locks, so efficient waiting would be impossible.

Example problem:
```java
// Without lock
Task task = taskQueue.poll();  // Thread 1 gets Task A
// Meanwhile, Thread 2 also executes
Task task = taskQueue.poll();  // Thread 2 gets Task A again OR gets a corrupt reference
```

### Without Condition Variables

If we removed condition variables but kept locks:

1. **Busy Waiting**: The scheduler would need to continuously check if tasks are ready:
   ```java
   while (true) {
       lock.lock();
       try {
           if (!taskQueue.isEmpty() && taskQueue.peek().getScheduledTime() <= System.currentTimeMillis()) {
               // Execute task
           }
       } finally {
           lock.unlock();
       }
       // Inefficient sleep to avoid CPU hogging
       Thread.sleep(10);
   }
   ```

2. **Resource Waste**: Constant CPU usage even when idle, wasting system resources.

3. **Timing Inaccuracy**: Tasks might execute late due to the polling interval.

4. **No Wake-up Mechanism**: When a new task is added that should run before all existing tasks, the scheduler wouldn't know to wake up and process it immediately.

### Without Priority Queue

If we used a regular List instead of a PriorityQueue:

1. **O(n) Insertion**: We'd need to manually sort tasks or scan the entire list to find the right insertion point.

2. **O(n) Finding Next Task**: We'd need to iterate through the entire list to find the next task to execute.

3. **Complex Task Management**: Code would be significantly more complex to maintain proper ordering.

Example alternative:
```java
// Without priority queue
List<Task> taskList = new ArrayList<>();
// To find next task:
Task nextTask = null;
for (Task t : taskList) {
    if (nextTask == null || t.getScheduledTime() < nextTask.getScheduledTime()) {
        nextTask = t;
    }
}
```

### Without ThreadPoolExecutor

If we executed tasks directly in the scheduler thread:

1. **No Concurrency**: Only one task could execute at a time, even if they're independent.

2. **Scheduler Blocking**: The main scheduler would be blocked during task execution, potentially delaying other tasks.

3. **No Thread Management**: No ability to limit, prioritize, or manage thread resources.

4. **No Separation of Concerns**: The scheduler would be responsible for both scheduling and execution.

Example problem:
```java
// Without thread pool
case RUN_ONCE -> {
    task.getTask().run(); // Blocks scheduler until task completes
}
```

### Without Task Class Encapsulation

If we didn't encapsulate task metadata:

1. **Multiple Data Structures**: We'd need separate collections for different task attributes.

2. **Complex Coordination**: Coordinating among these structures would be error-prone.

3. **Limited Extensibility**: Adding new task types or attributes would require changes throughout the code.

4. **Data Inconsistency**: Higher risk of inconsistent states between related pieces of data.

## Conclusion

This scheduler service demonstrates the use of Java's concurrency primitives to implement a flexible task scheduling system. By using locks, conditions, and thread pools, it provides efficient task execution while maintaining thread safety and proper timing.

Each component plays a vital role in ensuring the scheduler functions correctly, efficiently, and safely in a multi-threaded environment. Understanding the purpose of each part and how they work together is key to both implementing and maintaining such a system.