package org.example.traveljava.controller;

import org.example.traveljava.entity.Order;
import org.example.traveljava.service.FlightService;
import org.example.traveljava.service.booking.FlightOffer;
import org.example.traveljava.util.AuthUtils;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.vo.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 机票接口 — 航班搜索（mock/real 供应方）+ 机票下单。
 */
@RestController
@RequestMapping("/api/flight")
@io.swagger.v3.oas.annotations.tags.Tag(name = "电商")
public class FlightController {

    private final FlightService flightService;
    private final JwtUtil jwtUtil;

    public FlightController(FlightService flightService, JwtUtil jwtUtil) {
        this.flightService = flightService;
        this.jwtUtil = jwtUtil;
    }

    /** 航班搜索 — GET /api/flight/search?fromCity=北京&toCity=上海&date=2026-08-10 */
    @GetMapping("/search")
    public Result<List<FlightOffer>> search(@RequestParam String fromCity,
                                            @RequestParam String toCity,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(flightService.searchFlights(fromCity, toCity, date));
    }

    /** 机票下单 — POST /api/flight/book（需登录），创建 flight 订单（pending） */
    @PostMapping("/book")
    public Result<Order> book(@RequestHeader("Authorization") String auth,
                              @RequestBody Map<String, Object> params) {
        Long userId = AuthUtils.requireUserId(auth, jwtUtil);
        return Result.ok(flightService.bookFlight(userId, params));
    }
}
