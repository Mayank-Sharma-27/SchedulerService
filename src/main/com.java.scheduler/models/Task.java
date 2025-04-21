package models;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
public class Task {
    private Runnable task;
    private Long scheduledTime;
    private Long delay;
    private Long recurringTime;
    private ScheduledTaskType taskType;
    private TimeUnit timeUnit;

    public enum ScheduledTaskType {
        RUN_ONCE, RECUR, RECUR_WITH_WAIT;
    }

}
