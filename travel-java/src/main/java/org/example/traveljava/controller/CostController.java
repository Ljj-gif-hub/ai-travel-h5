package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.dto.CostBreakdownDTO;
import org.example.traveljava.service.CostCalculationService;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 费用计算控制器
 * 提供旅行费用预算明细的计算接口
 * 结合目的地、天数、人数和酒店选择生成完整费用分解
 */
@RestController
@RequestMapping("/api/cost")
public class CostController {

    private static final Logger log = LoggerFactory.getLogger(CostController.class);

    private final CostCalculationService costCalculationService;

    public CostController(CostCalculationService costCalculationService) {
        this.costCalculationService = costCalculationService;
    }

    /**
     * 费用明细计算接口
     * 根据目的地、天数、人数和可选酒店ID列表，计算各类费用的预估明细
     *
     * 请求体示例：
     * {
     *   "destination": "北京",
     *   "days": 5,
     *   "people": 2,
     *   "hotelIds": [9001, 9005],
     *   "preferences": "家庭休闲"
     * }
     *
     * @param body 请求参数Map
     * @return 费用明细DTO
     */
    @PostMapping("/breakdown")
    @RateLimit(max = 20, duration = 60, key = "cost_breakdown")
    public Result<CostBreakdownDTO> calculateBreakdown(@RequestBody Map<String, Object> body) {
        log.info("费用明细计算请求：{}", body);

        try {
            // 解析必填参数
            String destination = (String) body.get("destination");
            if (destination == null || destination.trim().isEmpty()) {
                log.warn("费用计算参数错误：目的地为空");
                return Result.fail("请提供目的地");
            }

            Object daysObj = body.get("days");
            if (daysObj == null) {
                log.warn("费用计算参数错误：天数为空");
                return Result.fail("请提供出行天数");
            }
            int days = ((Number) daysObj).intValue();
            if (days < 1 || days > 60) {
                log.warn("费用计算参数错误：天数={}", days);
                return Result.fail("出行天数需在1-60之间");
            }

            Object peopleObj = body.get("people");
            if (peopleObj == null) {
                log.warn("费用计算参数错误：人数为空");
                return Result.fail("请提供出行人数");
            }
            int people = ((Number) peopleObj).intValue();
            if (people < 1 || people > 50) {
                log.warn("费用计算参数错误：人数={}", people);
                return Result.fail("出行人数需在1-50之间");
            }

            // 解析可选参数：酒店ID列表
            List<Long> hotelIds = null;
            Object hotelIdsObj = body.get("hotelIds");
            if (hotelIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawList = (List<Object>) hotelIdsObj;
                // 安全转换为Long列表
                hotelIds = rawList.stream()
                        .filter(o -> o instanceof Number)
                        .map(o -> ((Number) o).longValue())
                        .toList();
            }

            // 解析可选参数：偏好
            String preferences = (String) body.get("preferences");

            // 执行费用计算
            CostBreakdownDTO breakdown;
            if (preferences != null && !preferences.trim().isEmpty()) {
                // 带偏好的AI估算
                breakdown = costCalculationService.aiEstimate(
                        destination.trim(), days, people, preferences.trim());
            } else {
                // 标准启发式计算
                breakdown = costCalculationService.calculateBreakdown(
                        destination.trim(), days, people, hotelIds);
            }

            log.info("费用明细计算成功：destination={}, totalCost={}",
                    destination, breakdown.getTotalCost());
            return Result.ok(breakdown);

        } catch (IllegalArgumentException e) {
            log.warn("费用计算参数错误：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("费用明细计算异常", e);
            return Result.fail("费用计算失败，请稍后重试");
        }
    }

    /**
     * 快速费用估算接口
     * 仅需目的地和天数即可获得大致预算范围
     *
     * @param destination 目的地（必填）
     * @param days        天数（必填）
     * @param people      人数（可选，默认1人）
     * @return 费用明细DTO
     */
    @GetMapping("/estimate")
    @RateLimit(max = 50, duration = 60, key = "cost_estimate")
    public Result<CostBreakdownDTO> quickEstimate(
            @RequestParam String destination,
            @RequestParam int days,
            @RequestParam(defaultValue = "1") int people) {

        log.info("快速费用估算：destination={}, days={}, people={}", destination, days, people);

        if (destination == null || destination.trim().isEmpty()) {
            return Result.fail("请提供目的地");
        }
        if (days < 1 || days > 60) {
            return Result.fail("出行天数需在1-60之间");
        }
        if (people < 1 || people > 50) {
            return Result.fail("出行人数需在1-50之间");
        }

        try {
            CostBreakdownDTO breakdown = costCalculationService.calculateBreakdown(
                    destination.trim(), days, people, null);
            log.info("快速估算成功：totalCost={}", breakdown.getTotalCost());
            return Result.ok(breakdown);
        } catch (Exception e) {
            log.error("快速费用估算异常", e);
            return Result.fail("费用估算失败，请稍后重试");
        }
    }
}
