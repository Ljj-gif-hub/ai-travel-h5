package org.example.traveljava.service;

import org.example.traveljava.dto.CostBreakdownDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 旅行费用计算服务
 * 结合酒店实际价格和目的地启发式算法，生成完整的旅行费用预算明细
 * 当实际酒店数据不可用时，使用估算模型作为兜底方案
 */
@Service
public class CostCalculationService {

    private static final Logger log = LoggerFactory.getLogger(CostCalculationService.class);

    private final HotelService hotelService;

    public CostCalculationService(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    /**
     * 目的地人均日均费用基准（元/人/天）
     * 用于在没有实际酒店数据时提供合理的估算
     */
    private static final Map<String, BigDecimal> DESTINATION_BASE_COST = new HashMap<>();

    static {
        // 国内热门旅游城市日均基准（不含大交通）
        DESTINATION_BASE_COST.put("北京", new BigDecimal("600"));
        DESTINATION_BASE_COST.put("上海", new BigDecimal("650"));
        DESTINATION_BASE_COST.put("广州", new BigDecimal("500"));
        DESTINATION_BASE_COST.put("深圳", new BigDecimal("550"));
        DESTINATION_BASE_COST.put("杭州", new BigDecimal("500"));
        DESTINATION_BASE_COST.put("成都", new BigDecimal("450"));
        DESTINATION_BASE_COST.put("重庆", new BigDecimal("400"));
        DESTINATION_BASE_COST.put("西安", new BigDecimal("400"));
        DESTINATION_BASE_COST.put("三亚", new BigDecimal("700"));
        DESTINATION_BASE_COST.put("大理", new BigDecimal("380"));
        DESTINATION_BASE_COST.put("丽江", new BigDecimal("380"));
        DESTINATION_BASE_COST.put("厦门", new BigDecimal("500"));
        DESTINATION_BASE_COST.put("桂林", new BigDecimal("350"));
        DESTINATION_BASE_COST.put("拉萨", new BigDecimal("450"));
        // 境外热门城市（已换算为人民币）
        DESTINATION_BASE_COST.put("巴黎", new BigDecimal("1500"));
        DESTINATION_BASE_COST.put("东京", new BigDecimal("1200"));
        DESTINATION_BASE_COST.put("首尔", new BigDecimal("800"));
        DESTINATION_BASE_COST.put("曼谷", new BigDecimal("350"));
        DESTINATION_BASE_COST.put("新加坡", new BigDecimal("900"));
        DESTINATION_BASE_COST.put("纽约", new BigDecimal("1800"));
        DESTINATION_BASE_COST.put("伦敦", new BigDecimal("1500"));
        DESTINATION_BASE_COST.put("悉尼", new BigDecimal("1200"));
    }

    /**
     * 计算完整的旅行费用明细
     *
     * @param destination 目的地城市
     * @param days        出行天数
     * @param people      出行人数
     * @param hotelIds    选中的酒店ID列表（可为null或空）
     * @return 费用明细DTO
     */
    public CostBreakdownDTO calculateBreakdown(String destination, int days, int people,
                                                List<Long> hotelIds) {
        log.info("开始计算费用明细：destination={}, days={}, people={}, hotelIds={}",
                destination, days, people, hotelIds);

        CostBreakdownDTO breakdown = new CostBreakdownDTO();

        // 获取基准日均费用
        BigDecimal baseCostPerDayPerPerson = DESTINATION_BASE_COST.getOrDefault(
                destination, new BigDecimal("500"));

        // ---- 1. 酒店费用 ----
        BigDecimal hotelCost = BigDecimal.ZERO;
        int nights = Math.max(1, days - 1); // 住 days-1 晚（最后一天退房）
        if (hotelIds != null && !hotelIds.isEmpty()) {
            hotelCost = hotelService.calculateHotelCost(hotelIds, nights);
        }
        if (hotelCost.compareTo(BigDecimal.ZERO) == 0) {
            // 无实际酒店数据时，使用估算：基准费用的50%用于住宿
            hotelCost = baseCostPerDayPerPerson
                    .multiply(new BigDecimal("0.50"))
                    .multiply(BigDecimal.valueOf(nights))
                    .multiply(BigDecimal.valueOf(Math.max(1, people / 2 + 1))); // 按房间数估算
        }
        breakdown.setHotelCost(hotelCost.setScale(2, RoundingMode.HALF_UP));

        // ---- 2. 门票费用 ----
        // 估算：基准费用的15%，境外更高（博物馆/景点门票）
        BigDecimal ticketRatio;
        if (isInternational(destination)) {
            ticketRatio = new BigDecimal("0.20");
        } else {
            ticketRatio = new BigDecimal("0.15");
        }
        BigDecimal ticketCost = baseCostPerDayPerPerson
                .multiply(ticketRatio)
                .multiply(BigDecimal.valueOf(days))
                .multiply(BigDecimal.valueOf(people));
        breakdown.setTicketCost(ticketCost.setScale(2, RoundingMode.HALF_UP));

        // ---- 3. 餐饮费用 ----
        // 估算：基准费用的20%
        BigDecimal foodCost = baseCostPerDayPerPerson
                .multiply(new BigDecimal("0.20"))
                .multiply(BigDecimal.valueOf(days))
                .multiply(BigDecimal.valueOf(people));
        breakdown.setFoodCost(foodCost.setScale(2, RoundingMode.HALF_UP));

        // ---- 4. 交通费用 ----
        // 估算：基准费用的10%（市内交通）+ 大交通估算
        BigDecimal localTransport = baseCostPerDayPerPerson
                .multiply(new BigDecimal("0.10"))
                .multiply(BigDecimal.valueOf(days))
                .multiply(BigDecimal.valueOf(people));

        // 大交通估算（往返机票/火车票）
        BigDecimal longDistanceTransport;
        if (isInternational(destination)) {
            longDistanceTransport = new BigDecimal("5000").multiply(BigDecimal.valueOf(people));
        } else {
            longDistanceTransport = new BigDecimal("1500").multiply(BigDecimal.valueOf(people));
        }
        BigDecimal transportCost = localTransport.add(longDistanceTransport);
        breakdown.setTransportCost(transportCost.setScale(2, RoundingMode.HALF_UP));

        // ---- 5. 其他费用 ----
        // 估算：基准费用的5%（购物、纪念品、保险、应急）
        BigDecimal otherCost = baseCostPerDayPerPerson
                .multiply(new BigDecimal("0.05"))
                .multiply(BigDecimal.valueOf(days))
                .multiply(BigDecimal.valueOf(people));
        breakdown.setOtherCost(otherCost.setScale(2, RoundingMode.HALF_UP));

        // ---- 6. 总费用 ----
        BigDecimal totalCost = hotelCost.add(ticketCost).add(foodCost)
                .add(transportCost).add(otherCost);
        breakdown.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));

        // ---- 7. 费用说明 ----
        StringBuilder noteBuilder = new StringBuilder();
        noteBuilder.append("费用估算说明：");
        noteBuilder.append("人均日均消费约").append(baseCostPerDayPerPerson).append("元，");
        noteBuilder.append("共").append(days).append("天").append(people).append("人。");
        noteBuilder.append("酒店费用占约");
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            int hotelPct = hotelCost.multiply(new BigDecimal("100"))
                    .divide(totalCost, 0, RoundingMode.HALF_UP).intValue();
            noteBuilder.append(hotelPct).append("%，");
        }
        noteBuilder.append("含往返大交通预估。");
        noteBuilder.append("实际费用可能因季节、预订时间等因素浮动，以上为参考估算。");
        breakdown.setDetailNote(noteBuilder.toString());

        log.info("费用明细计算完成：totalCost={}, hotelCost={}, ticketCost={}, foodCost={}, transportCost={}",
                breakdown.getTotalCost(), breakdown.getHotelCost(),
                breakdown.getTicketCost(), breakdown.getFoodCost(),
                breakdown.getTransportCost());

        return breakdown;
    }

    /**
     * AI费用估算（占位方法）
     * 预留用于接入AI模型进行更精准的费用估算
     * 当前返回基于启发式的估算结果
     *
     * @param destination 目的地
     * @param days        天数
     * @param people      人数
     * @param preferences 偏好描述
     * @return 费用明细DTO
     */
    public CostBreakdownDTO aiEstimate(String destination, int days, int people,
                                        String preferences) {
        log.info("AI费用估算（当前为启发式占位）：destination={}, days={}, people={}, preferences={}",
                destination, days, people, preferences);

        // 当前阶段使用启发式算法，后续可接入AI模型进行更精准的估算
        CostBreakdownDTO breakdown = calculateBreakdown(destination, days, people, null);

        // 根据偏好微调
        if (preferences != null && !preferences.isEmpty()) {
            String lowerPref = preferences.toLowerCase();
            // 豪华偏好上调20%
            if (lowerPref.contains("豪华") || lowerPref.contains("奢华") || lowerPref.contains("高端")) {
                BigDecimal factor = new BigDecimal("1.20");
                breakdown.setHotelCost(breakdown.getHotelCost().multiply(factor));
                breakdown.setTotalCost(breakdown.getTotalCost().multiply(factor));
                breakdown.setDetailNote(breakdown.getDetailNote() + "（已根据豪华偏好上调）");
            }
            // 经济偏好下调20%
            if (lowerPref.contains("经济") || lowerPref.contains("穷游") || lowerPref.contains("背包")) {
                BigDecimal factor = new BigDecimal("0.80");
                breakdown.setHotelCost(breakdown.getHotelCost().multiply(factor));
                breakdown.setTotalCost(breakdown.getTotalCost().multiply(factor));
                breakdown.setDetailNote(breakdown.getDetailNote() + "（已根据经济偏好下调）");
            }
        }

        return breakdown;
    }

    /**
     * 判断是否为境外目的地（简单规则）
     */
    private boolean isInternational(String destination) {
        if (destination == null) {
            return false;
        }
        // 简单判断：如果目的地不在中文常见国内城市列表中，视为境外
        return DESTINATION_BASE_COST.containsKey(destination)
                && (destination.equals("巴黎") || destination.equals("东京")
                    || destination.equals("首尔") || destination.equals("曼谷")
                    || destination.equals("新加坡") || destination.equals("纽约")
                    || destination.equals("伦敦") || destination.equals("悉尼"));
    }
}
