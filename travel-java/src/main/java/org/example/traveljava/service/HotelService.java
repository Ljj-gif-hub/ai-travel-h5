package org.example.traveljava.service;

import org.example.traveljava.entity.Hotel;
import org.example.traveljava.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 酒店服务类
 * 提供酒店搜索、详情查询和费用计算功能
 * 内置常见城市的种子数据，当数据库为空时自动回退到内存数据
 */
@Service
public class HotelService {

    private static final Logger log = LoggerFactory.getLogger(HotelService.class);

    private final HotelRepository hotelRepository;

    /**
     * 简单内存缓存，减少重复数据库查询
     * key: "city_" + cityName, value: 该城市的酒店列表
     */
    private final ConcurrentHashMap<String, List<Hotel>> cityCache = new ConcurrentHashMap<>();

    /**
     * 缓存过期时间戳
     * key: 与cityCache相同的key, value: 过期时间（毫秒）
     */
    private final ConcurrentHashMap<String, Long> cacheExpiry = new ConcurrentHashMap<>();

    /** 缓存有效期：30分钟 */
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    /**
     * 搜索酒店
     * 支持按城市、区域、价格区间筛选，支持分页
     * 当数据库中无数据时，自动回退到内置种子数据
     *
     * @param city     城市名称（必填）
     * @param district 区域名称（可选）
     * @param minPrice 最低价格（可选）
     * @param maxPrice 最高价格（可选）
     * @param page     页码（从0开始）
     * @param size     每页条数
     * @return Map包含"list"（酒店列表）和"total"（总记录数）
     */
    public Map<String, Object> searchHotels(String city, String district,
                                            BigDecimal minPrice, BigDecimal maxPrice,
                                            int page, int size) {
        log.info("搜索酒店：city={}, district={}, minPrice={}, maxPrice={}, page={}, size={}",
                city, district, minPrice, maxPrice, page, size);

        // 获取该城市的所有酒店（优先缓存，其次数据库，最后种子数据）
        List<Hotel> cityHotels = getHotelsByCity(city);

        // 按区域筛选
        if (district != null && !district.trim().isEmpty()) {
            cityHotels = cityHotels.stream()
                    .filter(h -> district.equals(h.getDistrict()))
                    .collect(Collectors.toList());
        }

        // 按价格区间筛选
        if (minPrice != null || maxPrice != null) {
            BigDecimal min = (minPrice != null) ? minPrice : BigDecimal.ZERO;
            BigDecimal max = (maxPrice != null) ? maxPrice : new BigDecimal("999999");
            cityHotels = cityHotels.stream()
                    .filter(h -> h.getPricePerNight() != null
                            && h.getPricePerNight().compareTo(min) >= 0
                            && h.getPricePerNight().compareTo(max) <= 0)
                    .collect(Collectors.toList());
        }

        int total = cityHotels.size();

        // 手动分页
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, cityHotels.size());
        List<Hotel> pagedList;
        if (fromIndex >= cityHotels.size()) {
            pagedList = Collections.emptyList();
        } else {
            pagedList = cityHotels.subList(fromIndex, toIndex);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", pagedList);
        result.put("total", total);

        log.info("酒店搜索结果：total={}, 当前页记录数={}", total, pagedList.size());
        return result;
    }

    /**
     * 根据ID获取酒店详情
     *
     * @param id 酒店ID
     * @return 酒店实体，如果不存在则抛出异常
     */
    public Hotel getHotelById(Long id) {
        // 先从数据库查询
        Hotel hotel = hotelRepository.findById(id).orElse(null);
        if (hotel != null) {
            return hotel;
        }

        // 数据库中没有，尝试在所有种子数据中查找
        log.info("数据库中未找到酒店id={}，尝试从种子数据中查找", id);
        for (String city : new String[]{"北京", "上海", "巴黎"}) {
            List<Hotel> seedHotels = getSeedHotels(city);
            for (Hotel h : seedHotels) {
                if (h.getId().equals(id)) {
                    return h;
                }
            }
        }

        throw new IllegalArgumentException("酒店不存在，id=" + id);
    }

    /**
     * 计算多间酒店多晚的总费用
     * 每间酒店住 nights 晚，累加所有酒店的费用
     *
     * @param hotelIds 酒店ID列表
     * @param nights   入住天数（晚数）
     * @return 总酒店费用
     */
    public BigDecimal calculateHotelCost(List<Long> hotelIds, int nights) {
        if (hotelIds == null || hotelIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Long hotelId : hotelIds) {
            try {
                Hotel hotel = getHotelById(hotelId);
                if (hotel.getPricePerNight() != null) {
                    BigDecimal hotelTotal = hotel.getPricePerNight()
                            .multiply(BigDecimal.valueOf(nights));
                    total = total.add(hotelTotal);
                }
            } catch (IllegalArgumentException e) {
                log.warn("计算酒店费用时未找到酒店：id={}", hotelId);
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取某个城市的所有酒店列表
     * 查询优先级：内存缓存 → 数据库 → 种子数据
     */
    private List<Hotel> getHotelsByCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cacheKey = "city_" + city;

        // 检查缓存是否命中且未过期
        Long expiry = cacheExpiry.get(cacheKey);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            List<Hotel> cached = cityCache.get(cacheKey);
            if (cached != null) {
                log.debug("酒店缓存命中：city={}, count={}", city, cached.size());
                return cached;
            }
        }

        // 从数据库查询
        List<Hotel> dbHotels = hotelRepository.findByCity(city);
        if (dbHotels != null && !dbHotels.isEmpty()) {
            log.info("酒店数据库命中：city={}, count={}", city, dbHotels.size());
            cityCache.put(cacheKey, dbHotels);
            cacheExpiry.put(cacheKey, System.currentTimeMillis() + CACHE_TTL_MS);
            return dbHotels;
        }

        // 数据库无数据，回退到种子数据
        log.info("数据库无酒店数据（city={}），使用种子数据", city);
        List<Hotel> seedHotels = getSeedHotels(city);
        if (!seedHotels.isEmpty()) {
            cityCache.put(cacheKey, seedHotels);
            cacheExpiry.put(cacheKey, System.currentTimeMillis() + CACHE_TTL_MS);
        }
        return seedHotels;
    }

    /**
     * 获取内置种子数据
     * 为常见城市提供预设的酒店列表，确保前端体验不依赖数据库
     */
    private List<Hotel> getSeedHotels(String city) {
        switch (city) {
            case "北京":
                return createBeijingSeedHotels();
            case "上海":
                return createShanghaiSeedHotels();
            case "巴黎":
                return createParisSeedHotels();
            default:
                log.warn("城市'{}'无种子数据，返回空列表", city);
                return Collections.emptyList();
        }
    }

    /**
     * 北京种子酒店数据（10家）
     */
    private List<Hotel> createBeijingSeedHotels() {
        List<Hotel> hotels = new ArrayList<>();

        hotels.add(buildSeedHotel(9001L, "北京王府井希尔顿酒店", "北京", "东城区",
                "王府井大街8号", 39.9142, 116.4105,
                new BigDecimal("1280.00"), 4.8,
                "https://picsum.photos/id/1/400/300",
                "[\"免费WiFi\",\"室内泳池\",\"健身中心\",\"行政酒廊\",\"停车场\"]"));

        hotels.add(buildSeedHotel(9002L, "北京国贸大酒店", "北京", "朝阳区",
                "建国门外大街1号", 39.9087, 116.4605,
                new BigDecimal("1580.00"), 4.9,
                "https://picsum.photos/id/2/400/300",
                "[\"免费WiFi\",\"无边泳池\",\"SPA中心\",\"商务中心\",\"接机服务\"]"));

        hotels.add(buildSeedHotel(9003L, "北京前门建国饭店", "北京", "西城区",
                "前门西大街14号", 39.9005, 116.3915,
                new BigDecimal("580.00"), 4.3,
                "https://picsum.photos/id/3/400/300",
                "[\"免费WiFi\",\"中式餐厅\",\"茶室\",\"行李寄存\"]"));

        hotels.add(buildSeedHotel(9004L, "北京颐和安缦酒店", "北京", "海淀区",
                "颐和园宫门前街1号", 39.9982, 116.2752,
                new BigDecimal("3200.00"), 4.9,
                "https://picsum.photos/id/4/400/300",
                "[\"免费WiFi\",\"私人庭院\",\"瑜伽室\",\"图书馆\",\"管家服务\"]"));

        hotels.add(buildSeedHotel(9005L, "北京丽晶酒店", "北京", "东城区",
                "金宝街99号", 39.9171, 116.4238,
                new BigDecimal("960.00"), 4.6,
                "https://picsum.photos/id/5/400/300",
                "[\"免费WiFi\",\"室内泳池\",\"桑拿房\",\"会议室\"]"));

        hotels.add(buildSeedHotel(9006L, "北京中关村皇冠假日酒店", "北京", "海淀区",
                "知春路106号", 39.9820, 116.3336,
                new BigDecimal("720.00"), 4.4,
                "https://picsum.photos/id/6/400/300",
                "[\"免费WiFi\",\"健身中心\",\"自助早餐\",\"洗衣服务\"]"));

        hotels.add(buildSeedHotel(9007L, "北京三里屯通盈中心洲际酒店", "北京", "朝阳区",
                "南三里屯路1号", 39.9318, 116.4551,
                new BigDecimal("1380.00"), 4.7,
                "https://picsum.photos/id/7/400/300",
                "[\"免费WiFi\",\"顶层酒吧\",\"室内泳池\",\"SPA\",\"代客泊车\"]"));

        hotels.add(buildSeedHotel(9008L, "北京什刹海皮影文化酒店", "北京", "西城区",
                "松树街24号", 39.9390, 116.3835,
                new BigDecimal("480.00"), 4.2,
                "https://picsum.photos/id/8/400/300",
                "[\"免费WiFi\",\"文化体验\",\"四合院庭院\",\"自行车租赁\"]"));

        hotels.add(buildSeedHotel(9009L, "北京盘古大观酒店", "北京", "朝阳区",
                "北四环中路27号", 39.9882, 116.3922,
                new BigDecimal("2200.00"), 4.8,
                "https://picsum.photos/id/9/400/300",
                "[\"免费WiFi\",\"全景落地窗\",\"空中花园\",\"私人管家\",\"直升机停机坪\"]"));

        hotels.add(buildSeedHotel(9010L, "北京徽商故里酒店", "北京", "东城区",
                "南锣鼓巷16号", 39.9371, 116.4032,
                new BigDecimal("350.00"), 4.0,
                "https://picsum.photos/id/10/400/300",
                "[\"免费WiFi\",\"胡同庭院\",\"徽派建筑\",\"茶文化体验\"]"));

        return hotels;
    }

    /**
     * 上海种子酒店数据（8家）
     */
    private List<Hotel> createShanghaiSeedHotels() {
        List<Hotel> hotels = new ArrayList<>();

        hotels.add(buildSeedHotel(9101L, "上海外滩华尔道夫酒店", "上海", "黄浦区",
                "中山东一路2号", 31.2397, 121.4903,
                new BigDecimal("2600.00"), 4.9,
                "https://picsum.photos/id/11/400/300",
                "[\"免费WiFi\",\"外滩江景\",\"米其林餐厅\",\"SPA\",\"管家服务\"]"));

        hotels.add(buildSeedHotel(9102L, "上海浦东丽思卡尔顿酒店", "上海", "浦东新区",
                "世纪大道8号", 31.2354, 121.5016,
                new BigDecimal("2800.00"), 4.9,
                "https://picsum.photos/id/12/400/300",
                "[\"免费WiFi\",\"天际泳池\",\"行政酒廊\",\"豪车接送\"]"));

        hotels.add(buildSeedHotel(9103L, "上海静安瑞吉酒店", "上海", "静安区",
                "北京西路1008号", 31.2288, 121.4503,
                new BigDecimal("1680.00"), 4.7,
                "https://picsum.photos/id/13/400/300",
                "[\"免费WiFi\",\"室内泳池\",\"爵士酒吧\",\"24小时管家\"]"));

        hotels.add(buildSeedHotel(9104L, "上海新天地朗廷酒店", "上海", "黄浦区",
                "马当路99号", 31.2197, 121.4734,
                new BigDecimal("1200.00"), 4.6,
                "https://picsum.photos/id/14/400/300",
                "[\"免费WiFi\",\"英式下午茶\",\"健身中心\",\"商务中心\"]"));

        hotels.add(buildSeedHotel(9105L, "上海豫园万丽酒店", "上海", "黄浦区",
                "河南南路159号", 31.2267, 121.4890,
                new BigDecimal("780.00"), 4.4,
                "https://picsum.photos/id/15/400/300",
                "[\"免费WiFi\",\"城隍庙景观\",\"屋顶酒吧\",\"会议室\"]"));

        hotels.add(buildSeedHotel(9106L, "上海徐汇禧玥酒店", "上海", "徐汇区",
                "虹桥路1号", 31.1970, 121.4310,
                new BigDecimal("620.00"), 4.3,
                "https://picsum.photos/id/16/400/300",
                "[\"免费WiFi\",\"法式庭院\",\"图书馆\",\"自行车租赁\"]"));

        hotels.add(buildSeedHotel(9107L, "上海虹桥康得思酒店", "上海", "闵行区",
                "申长路688号", 31.1969, 121.3169,
                new BigDecimal("880.00"), 4.5,
                "https://picsum.photos/id/17/400/300",
                "[\"免费WiFi\",\"机场班车\",\"室内泳池\",\"川菜餐厅\"]"));

        hotels.add(buildSeedHotel(9108L, "上海五角场凯悦酒店", "上海", "杨浦区",
                "国定东路88号", 31.2988, 121.5150,
                new BigDecimal("680.00"), 4.3,
                "https://picsum.photos/id/18/400/300",
                "[\"免费WiFi\",\"健身中心\",\"自助早餐\",\"洗衣服务\"]"));

        return hotels;
    }

    /**
     * 巴黎种子酒店数据（6家）
     */
    private List<Hotel> createParisSeedHotels() {
        List<Hotel> hotels = new ArrayList<>();

        hotels.add(buildSeedHotel(9201L, "巴黎丽兹酒店 (Ritz Paris)", "巴黎", "第一区",
                "15 Place Vendôme, 75001 Paris", 48.8683, 2.3288,
                new BigDecimal("8500.00"), 4.9,
                "https://picsum.photos/id/19/400/300",
                "[\"免费WiFi\",\"米其林二星餐厅\",\"香奈儿SPA\",\"管家服务\",\"室内泳池\"]"));

        hotels.add(buildSeedHotel(9202L, "巴黎香格里拉酒店", "巴黎", "第十六区",
                "10 Avenue d'Iéna, 75116 Paris", 48.8650, 2.2937,
                new BigDecimal("7200.00"), 4.8,
                "https://picsum.photos/id/20/400/300",
                "[\"免费WiFi\",\"埃菲尔铁塔景观\",\"法式花园\",\"室内泳池\",\"粤菜餐厅\"]"));

        hotels.add(buildSeedHotel(9203L, "巴黎铂尔曼埃菲尔铁塔酒店", "巴黎", "第十五区",
                "18 Avenue de Suffren, 75015 Paris", 48.8555, 2.2923,
                new BigDecimal("3200.00"), 4.5,
                "https://picsum.photos/id/21/400/300",
                "[\"免费WiFi\",\"铁塔景观房\",\"健身中心\",\"露台餐厅\"]"));

        hotels.add(buildSeedHotel(9204L, "巴黎左岸美居酒店", "巴黎", "第六区",
                "6 Rue de Vaugirard, 75006 Paris", 48.8498, 2.3400,
                new BigDecimal("1800.00"), 4.2,
                "https://picsum.photos/id/22/400/300",
                "[\"免费WiFi\",\"拉丁区中心\",\"法式早餐\",\"24小时前台\"]"));

        hotels.add(buildSeedHotel(9205L, "巴黎蒙马特伊甸园酒店", "巴黎", "第十八区",
                "90 Rue des Martyrs, 75018 Paris", 48.8867, 2.3359,
                new BigDecimal("1100.00"), 4.0,
                "https://picsum.photos/id/23/400/300",
                "[\"免费WiFi\",\"圣心堂景观\",\"艺术家风格\",\"露台花园\"]"));

        hotels.add(buildSeedHotel(9206L, "巴黎玛黑区精品酒店", "巴黎", "第四区",
                "30 Rue des Archives, 75004 Paris", 48.8584, 2.3572,
                new BigDecimal("2500.00"), 4.4,
                "https://picsum.photos/id/24/400/300",
                "[\"免费WiFi\",\"设计师风格\",\"庭院早餐\",\"自行车租赁\",\"葡萄酒吧\"]"));

        return hotels;
    }

    /**
     * 快速构建一个种子酒店对象
     */
    private Hotel buildSeedHotel(Long id, String name, String city, String district,
                                  String address, Double latitude, Double longitude,
                                  BigDecimal pricePerNight, Double rating,
                                  String imageUrl, String amenities) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setDistrict(district);
        hotel.setAddress(address);
        hotel.setLatitude(latitude);
        hotel.setLongitude(longitude);
        hotel.setPricePerNight(pricePerNight);
        hotel.setRating(rating);
        hotel.setImageUrl(imageUrl);
        hotel.setAmenities(amenities);
        hotel.setCreatedAt(LocalDateTime.now());
        return hotel;
    }
}
