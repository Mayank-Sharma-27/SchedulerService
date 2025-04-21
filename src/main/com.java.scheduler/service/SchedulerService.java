package service;

import lombok.SneakyThrows;
import models.Task;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SchedulerService implements Runnable {

    private final PriorityQueue<Task> taskQueue;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final Lock lock;
    private final Condition condition;

    public SchedulerService() {
        this.taskQueue = new PriorityQueue<>(Comparator.comparingLong(Task::getScheduledTime));;
        this.threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        this.lock = new ReentrantLock();
        this.condition = this.lock.newCondition();
    }

    @Override
    public void run() {
        Long timeToSleep = 0l;
        while (true) {
            lock.lock();
            try {
                while (taskQueue.isEmpty()) {
                    this.condition.await();
                }

                while (!taskQueue.isEmpty()) {
                    timeToSleep = taskQueue.peek().getTimeUnit().toMillis(taskQueue.peek().getScheduledTime() - System.currentTimeMillis());
                    if (timeToSleep <= 0) {
                        break;
                    }
                    condition.await(timeToSleep, TimeUnit.MILLISECONDS);
                }

                Task task = taskQueue.poll();
                Long scheduledTime = 0l;

                switch (task.getTaskType()) {
                    case RUN_ONCE -> {
                        threadPoolExecutor.submit(task.getTask());
                    }
                    case RECUR -> {
                        scheduledTime = System.currentTimeMillis() + task.getTimeUnit().toMillis(task.getRecurringTime());;
                        threadPoolExecutor.submit(task.getTask());
                        task.setScheduledTime(scheduledTime);
                        taskQueue.add(task);
                    }
                    case RECUR_WITH_WAIT -> {
                        Future<?> future = threadPoolExecutor.submit(task.getTask());
                        future.get();
                        scheduledTime = System.currentTimeMillis() + task.getTimeUnit().toMillis(task.getRecurringTime());
                        task.setScheduledTime(scheduledTime);
                        taskQueue.add(task);

                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    public void scheduleTask(Runnable task, Long delay, TimeUnit timeUnit) {
        addTask(task, delay, null, timeUnit, Task.ScheduledTaskType.RUN_ONCE);
    }

    public void scheduleRecurringTask(Runnable task, Long delay, Long recurringTime ,TimeUnit timeUnit) {
        addTask(task, delay, recurringTime, timeUnit, Task.ScheduledTaskType.RECUR);
    }

    public void scheduleRecurringTaskWithWait(Runnable task, Long delay, Long recurringTime ,TimeUnit timeUnit) {
        addTask(task, delay, recurringTime, timeUnit, Task.ScheduledTaskType.RECUR_WITH_WAIT);
    }

    private void addTask(Runnable task, Long delay, Long recurringTime ,TimeUnit timeUnit, Task.ScheduledTaskType scheduledTaskType) {
        lock.lock();
        try {
            Long scheduledTime = System.currentTimeMillis() + timeUnit.toMillis(delay);;
            Task newTask = new Task(task, scheduledTime, delay, recurringTime, scheduledTaskType, timeUnit);
            taskQueue.add(newTask);
            condition.signalAll();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
