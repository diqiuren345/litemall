package org.linlinjava.litemall.core.task;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TaskService {
    private TaskService taskService;
    private final DelayQueue<Task> delayQueue = new DelayQueue<Task>();
    private ExecutorService executorService;

    @PostConstruct
    private void init() {
        taskService = this;

        executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Task task = delayQueue.take();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public void addTask(Task task){
        if(delayQueue.contains(task)){
            return;
        }
        delayQueue.add(task);
    }

    public void removeTask(Task task){
        delayQueue.remove(task);
    }

}
