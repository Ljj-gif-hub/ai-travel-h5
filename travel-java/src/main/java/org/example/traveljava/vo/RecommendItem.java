package org.example.traveljava.vo;

/**
 * 推荐结果条目（智能推荐引擎输出）。
 */
public class RecommendItem {

    /** 条目类型：attraction / destination / note / hotel 等（收藏 targetType） */
    private String targetType;
    /** 对应收藏 / 游记的 ID */
    private Long targetId;
    private String name;
    private String cover;
    /** 综合推荐分（仅供参考，未归一化） */
    private double score;
    /** 推荐理由（给用户展示） */
    private String reason;

    public RecommendItem() {
    }

    public RecommendItem(String targetType, Long targetId, String name, String cover, double score, String reason) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.name = name;
        this.cover = cover;
        this.score = score;
        this.reason = reason;
    }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
