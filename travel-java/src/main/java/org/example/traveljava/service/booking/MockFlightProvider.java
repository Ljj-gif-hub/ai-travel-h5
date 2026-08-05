package org.example.traveljava.service.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 默认航班供应方（mock）：按 出发城市×到达城市×日期 确定性生成航班，
 * 同一查询结果稳定（便于前端联调），价格/时长在合理区间内随机扰动。
 *
 * 后续接入真实渠道时切换 booking.flight.provider=real，业务层与前端零改动。
 */
@Component
@ConditionalOnProperty(prefix = "booking.flight", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockFlightProvider implements FlightProvider {

    private static final Logger log = LoggerFactory.getLogger(MockFlightProvider.class);

    private static final String[] AIRLINES = {"国航", "东航", "南航", "海航", "川航", "深航"};
    private static final String[] SLOTS = {"06:20", "08:35", "11:10", "13:45", "16:25", "19:05", "21:40"};

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public List<FlightOffer> search(String fromCity, String toCity, LocalDate date) {
        if (fromCity == null || toCity == null || fromCity.isBlank() || toCity.isBlank()) {
            throw new IllegalArgumentException("出发/到达城市不能为空");
        }
        if (fromCity.trim().equals(toCity.trim())) {
            throw new IllegalArgumentException("出发城市与到达城市不能相同");
        }
        if (date == null) {
            throw new IllegalArgumentException("请选择出行日期");
        }

        List<FlightOffer> offers = new ArrayList<>();
        int slots = Math.min(SLOTS.length, 6);
        for (int i = 0; i < slots; i++) {
            // 以 城市×日期×时段 作为随机种子 → 同一查询结果稳定
            long seed = String.format("%s>%s>%s>%s", fromCity, toCity, date, i).hashCode();
            Random rnd = new Random(seed);

            String airline = AIRLINES[rnd.nextInt(AIRLINES.length)];
            String flightNo = airlinePrefix(airline) + (100 + rnd.nextInt(900));
            int duration = 80 + rnd.nextInt(240);          // 80~320 分钟
            long basePrice = 380 + (rnd.nextInt(56) * 50);  // 380~3130，10 的倍数

            String departTime = SLOTS[i];
            String arrivalTime = addMinutes(departTime, duration);

            offers.add(new FlightOffer(flightNo, airline, fromCity, toCity,
                    date.toString(), departTime, arrivalTime, duration, "经济舱", basePrice));
            offers.add(new FlightOffer(flightNo + "J", airline, fromCity, toCity,
                    date.toString(), departTime, arrivalTime, duration, "商务舱", basePrice * 25 / 10));
        }
        log.info("[航班mock] {}→{} {} 生成 {} 班", fromCity, toCity, date, offers.size());
        return offers;
    }

    private String airlinePrefix(String airline) {
        return switch (airline) {
            case "国航" -> "CA";
            case "东航" -> "MU";
            case "南航" -> "CZ";
            case "海航" -> "HU";
            case "川航" -> "3U";
            default -> "ZH";
        };
    }

    private String addMinutes(String hhmm, int minutes) {
        int h = Integer.parseInt(hhmm.substring(0, 2));
        int m = Integer.parseInt(hhmm.substring(3, 5));
        LocalTime t = LocalTime.of(h, m).plusMinutes(minutes);
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
