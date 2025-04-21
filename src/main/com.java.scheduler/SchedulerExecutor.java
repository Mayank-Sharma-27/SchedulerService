import service.SchedulerService;

import java.util.concurrent.TimeUnit;

public class SchedulerExecutor {

    public static void main(String[] args) {
        SchedulerService schedulerService = new SchedulerService();
        Runnable task1 = createRunnableTask("Task1");
        schedulerService.scheduleTask(task1, 1L, TimeUnit.MILLISECONDS);

        Runnable task2 = createRunnableTask("Task2");
        schedulerService.scheduleTask(task2, 5L, TimeUnit.MILLISECONDS);

        Runnable task3 = createRunnableTask("Task3");
        schedulerService.scheduleRecurringTask(task3, 5L, 5L, TimeUnit.MILLISECONDS);

        Runnable task4 = createRunnableTask("Task4");
        schedulerService.scheduleRecurringTaskWithWait(task4, 5L, 1L, TimeUnit.MILLISECONDS);

        new Thread(schedulerService).start();
    }

    public static Runnable createRunnableTask(String task_name) {
        return () -> {
            System.out.printf("Starting %s at %d%n", task_name, System.currentTimeMillis());
            try {
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.printf("Ending %s at %d%n", task_name, System.currentTimeMillis());
        };
    }
}
