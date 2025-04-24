package service;

import models.Task;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SchedulerService implements Runnable {

    private final PriorityQueue<Task> taskQueue;
    private final ExecutorService executorService;
    private final Lock lock;
    private final Condition condition;

    public SchedulerService() {
        this.taskQueue = new PriorityQueue<>(Comparator.comparingLong(Task::getScheduledTime));;
        this.executorService = Executors.newCachedThreadPool();
        this.lock = new ReentrantLock();
        this.condition = this.lock.newCondition();
    }

    @Override
    public void run() {
        while (true) {
            lock.lock();
            try {
                while (taskQueue.isEmpty()) {
                    this.condition.await();
                }

                while (!taskQueue.isEmpty()) {
                    Long timeToSleep = taskQueue.peek().getScheduledTime() - System.currentTimeMillis();
                    // If the task is due break from loop and start
                    // executing the callback
                    if (timeToSleep <= 0) {
                        break;
                    }
                    // sleep until the earliest due callback can be executed
                    condition.await(timeToSleep, TimeUnit.MILLISECONDS);
                }
                // Because we have a min-heap the first element of the queue
                // is necessarily the one which is due.
                Task task = taskQueue.poll();
                Long scheduledTime = 0l;

                switch (task.getTaskType()) {
                    case RUN_ONCE -> {
                        executorService.submit(task.getTask());
                    }
                    case RECUR -> {
                        scheduledTime = System.currentTimeMillis() + task.getTimeUnit().toMillis(task.getRecurringTime());;
                        executorService.submit(task.getTask());
                        task.setScheduledTime(scheduledTime);
                        taskQueue.add(task);
                    }
                    case RECUR_WITH_WAIT -> {
                        executorService.submit(() -> {
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
