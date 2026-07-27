package org.example.traveljava.dto;

/**
 * AI 行程规划 7 个串行生成阶段
 */
public enum GenerateStep {

    DEMAND_ANALYZE("了解你的需求", 10),
    DEST_INFO_QUERY("查找目的地信息", 15),
    SPOT_QUERY("查询热门景点", 25),
    HOTEL_QUERY("挑选合适酒店", 41),
    TRAFFIC_PLAN("规划交通路线", 60),
    TIPS_SORT("整理出行贴士", 80),
    DAILY_ROUTE_ARRANGE("安排每日行程", 100);

    public final String label;
    public final int progress;

    GenerateStep(String label, int progress) {
        this.label = label;
        this.progress = progress;
    }
}
