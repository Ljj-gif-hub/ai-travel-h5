package org.example.traveljava.dto;

import java.util.List;
import java.util.Map;

/**
 * SSE 阶段进度推送 DTO
 * 每完成一个阶段向后端发送一次，前端实时更新进度UI
 */
public class GenerateProgressDTO {

    /** 当前阶段名称 */
    private String stepName;
    /** 总进度百分比 10,15,25,41,60,80,100 */
    private int progress;
    /** 当前状态: wait / doing / done */
    private String status;
    /** 阶段概要文字，如"已找到20个推荐景点" */
    private String summary;
    /** 预览卡片数据（景点列表/酒店/交通/贴士） */
    private Map<String, Object> previewData;
    /** 全部7个阶段完整状态 */
    private List<StepItem> allStepList;
    /** 用户偏好回显 */
    private TripUserPref userPref;
    /** 是否全部完成 */
    private boolean finish;
    /** 事件类型: progress-update / generate-finish / task-stop */
    private String eventType;

    public GenerateProgressDTO() {}

    /** 快捷构造 */
    public static GenerateProgressDTO progress(GenerateStep step, String summary,
                                                Map<String, Object> preview, List<StepItem> steps,
                                                TripUserPref pref) {
        GenerateProgressDTO dto = new GenerateProgressDTO();
        dto.stepName = step.label;
        dto.progress = step.progress;
        dto.status = "done";
        dto.summary = summary;
        dto.previewData = preview;
        dto.allStepList = steps;
        dto.userPref = pref;
        dto.finish = step == GenerateStep.DAILY_ROUTE_ARRANGE;
        dto.eventType = dto.finish ? "generate-finish" : "progress-update";
        return dto;
    }

    // getters & setters
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Map<String, Object> getPreviewData() { return previewData; }
    public void setPreviewData(Map<String, Object> previewData) { this.previewData = previewData; }

    public List<StepItem> getAllStepList() { return allStepList; }
    public void setAllStepList(List<StepItem> allStepList) { this.allStepList = allStepList; }

    public TripUserPref getUserPref() { return userPref; }
    public void setUserPref(TripUserPref userPref) { this.userPref = userPref; }

    public boolean isFinish() { return finish; }
    public void setFinish(boolean finish) { this.finish = finish; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    /** 单阶段状态项 */
    public static class StepItem {
        public String name;
        public int progress;
        public String status;  // wait / doing / done

        public StepItem() {}
        public StepItem(String name, int progress, String status) {
            this.name = name; this.progress = progress; this.status = status;
        }
    }

    /** 用户偏好回显 */
    public static class TripUserPref {
        public String companion;
        public List<String> styles;
        public String hotelLevel;
        public String cabinClass;
        public String pace;
        public String schedule;
    }
}
