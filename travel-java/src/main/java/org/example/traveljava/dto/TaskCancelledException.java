package org.example.traveljava.dto;

/**
 * 任务取消异常 — 跨层传递取消信号
 */
public class TaskCancelledException extends RuntimeException {
    private final String taskId;

    public TaskCancelledException(String taskId) {
        super("任务已取消: " + taskId);
        this.taskId = taskId;
    }

    public String getTaskId() { return taskId; }
}
