package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.entity.Hotel;
import org.example.traveljava.entity.Order;
import org.example.traveljava.service.HotelService;
import org.example.traveljava.service.OrderService;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 酒店控制器
 * 提供酒店搜索和详情查询的REST API
 * 支持按城市、区域、价格区间筛选，并返回分页结果
 */
@RestController
@RequestMapping("/api/hotel")
@io.swagger.v3.oas.annotations.tags.Tag(name = "电商")
public class HotelController {

    private static final Logger log = LoggerFactory.getLogger(HotelController.class);

    private final HotelService hotelService;
    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    public HotelController(HotelService hotelService, OrderService orderService, JwtUtil jwtUtil) {
        this.hotelService = hotelService;
        this.orderService = orderService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 酒店搜索接口
     * 支持多维筛选和分页
     *
     * @param city     城市名称（必填）
     * @param district 区域/商圈（可选）
     * @param minPrice 最低价格（可选）
     * @param maxPrice 最高价格（可选）
     * @param page     页码，从0开始，默认0
     * @param size     每页条数，默认10，最大50
     * @return 包含酒店列表和总数的Result
     */
    @GetMapping("/search")
    @RateLimit(max = 30, duration = 60, key = "hotel_search")
    public Result<Map<String, Object>> search(
            @RequestParam String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("酒店搜索请求：city={}, district={}, minPrice={}, maxPrice={}, page={}, size={}",
                city, district, minPrice, maxPrice, page, size);

        // 参数校验
        if (city == null || city.trim().isEmpty()) {
            log.warn("酒店搜索参数错误：城市名称为空");
            return Result.fail("请提供城市名称");
        }

        if (size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }
        if (page < 0) {
            page = 0;
        }

        try {
            Map<String, Object> result = hotelService.searchHotels(
                    city.trim(), district, minPrice, maxPrice, page, size);

            @SuppressWarnings("unchecked")
            List<Hotel> list = (List<Hotel>) result.get("list");
            // DB 分页路径返回 Long，内存种子路径返回 Integer，统一按 Number 处理避免 ClassCastException
            Number total = (Number) result.get("total");

            log.info("酒店搜索成功：city={}, total={}, 当前页={}", city, total, list.size());
            return Result.ok(result);
        } catch (Exception e) {
            log.error("酒店搜索异常：city={}", city, e);
            return Result.fail("酒店搜索失败，请稍后重试");
        }
    }

    /**
     * 酒店详情接口
     * 根据酒店ID获取完整的酒店信息
     *
     * @param id 酒店ID
     * @return 酒店详细信息
     */
    @GetMapping("/{id}")
    @RateLimit(max = 50, duration = 60, key = "hotel_detail")
    public Result<Hotel> getHotelDetail(@PathVariable Long id) {
        log.info("酒店详情请求：id={}", id);

        if (id == null || id <= 0) {
            log.warn("酒店详情参数错误：id={}", id);
            return Result.fail("无效的酒店ID");
        }

        try {
            Hotel hotel = hotelService.getHotelById(id);
            log.info("酒店详情查询成功：id={}, name={}", id, hotel.getName());
            return Result.ok(hotel);
        } catch (IllegalArgumentException e) {
            log.warn("酒店详情查询失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("酒店详情查询异常：id={}", id, e);
            return Result.fail("获取酒店详情失败，请稍后重试");
        }
    }

    /**
     * 酒店预订接口（需登录）
     * 校验酒店与日期 → 计算报价 → 创建 hotel 类型订单（pending）
     *
     * @param body {hotelId, checkIn(yyyy-MM-dd), checkOut(yyyy-MM-dd), rooms}
     * @return 订单摘要
     */
    @PostMapping("/book")
    @RateLimit(max = 10, duration = 60, key = "hotel_book")
    public Result<Map<String, Object>> book(@RequestHeader("Authorization") String authHeader,
                                            @RequestBody Map<String, Object> body) {
        Long userId = AuthUtils.requireUserId(authHeader, jwtUtil);
        try {
            Object hotelIdObj = body.get("hotelId");
            if (hotelIdObj == null) {
                return Result.fail("缺少酒店ID");
            }
            Long hotelId = ((Number) hotelIdObj).longValue();
            // CTRL-3 修复：LocalDate.parse 前校验 null/类型/格式/历史日期
            Object checkInObj = body.get("checkIn");
            Object checkOutObj = body.get("checkOut");
            if (!(checkInObj instanceof String) || !(checkOutObj instanceof String)) {
                return Result.fail("入住/离店日期格式不正确");
            }
            LocalDate checkIn;
            LocalDate checkOut;
            try {
                checkIn = LocalDate.parse((String) checkInObj);
                checkOut = LocalDate.parse((String) checkOutObj);
            } catch (Exception e) {
                return Result.fail("入住/离店日期格式不正确，请使用 yyyy-MM-dd 格式");
            }
            if (checkIn.isBefore(LocalDate.now()) || !checkOut.isAfter(checkIn)) {
                return Result.fail("日期不合法：入住日期不能早于今天，离店日期必须晚于入住日期");
            }
            int rooms = body.get("rooms") == null ? 1 : ((Number) body.get("rooms")).intValue();

            Map<String, Object> quote = hotelService.bookHotel(hotelId, checkIn, checkOut, rooms);
            Hotel hotel = (Hotel) quote.get("hotel");

            Map<String, Object> params = new HashMap<>();
            params.put("type", "hotel");
            params.put("hotelName", hotel.getName());
            params.put("checkInTime", checkIn.atStartOfDay());
            params.put("checkOutTime", checkOut.atStartOfDay());
            params.put("price", quote.get("totalPrice"));
            params.put("quantity", rooms);
            Order order = orderService.createOrder(userId, params);

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("status", order.getStatus());
            result.put("price", order.getPrice());
            result.put("hotelName", hotel.getName());
            result.put("nights", quote.get("nights"));
            result.put("rooms", quote.get("rooms"));
            log.info("酒店预订成功：userId={}, hotel={}, orderNo={}", userId, hotel.getName(), order.getOrderNo());
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("酒店预订失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (AuthUtils.AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("酒店预订异常", e);
            return Result.fail("预订失败，请稍后重试");
        }
    }
}
