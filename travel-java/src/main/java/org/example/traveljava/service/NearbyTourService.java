package org.example.traveljava.service;

import org.example.traveljava.dto.CityPointDTO;
import org.example.traveljava.dto.RouteCardDTO;
import org.example.traveljava.dto.SurroundCityDTO;
import org.example.traveljava.dto.SurroundTourVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 周边游 —— 精选种子数据服务。
 * 注意：数据为"示例/近似值"（高铁时长、票价为示意，非实时查询），可后续替换为真实数据源。
 * 图片只存城市名/景点名，由前端本地图库解析成 URL，见 {@link SurroundCityDTO} 说明。
 * 遵循 MapController 硬编码列表的先例（landmarks/metro/scenic-photos）。
 */
@Service
public class NearbyTourService {

    /** 可选出发城市（含坐标，供地图中心/切换） */
    private static final List<CityPointDTO> DEPARTURE_CITIES = List.of(
            point("深圳", 22.543, 114.058),
            point("北京", 39.904, 116.407)
    );

    /** 默认出发城市 */
    private static final String DEFAULT_DEPARTURE = "深圳";

    /** 出发城市 → 可到达城市列表 */
    private static final Map<String, List<SurroundCityDTO>> SURROUND = new LinkedHashMap<>();

    /** 热门路线 */
    private static final List<RouteCardDTO> ROUTES = new ArrayList<>();

    static {
        SURROUND.put("深圳", List.of(
                city("香港", "香港", 22.31, 114.16, 98, 14, "高铁14分钟 起", "¥64 起", 64,
                        Arrays.asList("维多利亚港", "迪士尼乐园", "太平山顶"), Arrays.asList("都市", "购物"),
                        "维港夜景与购物美食天堂"),
                city("广州", "广东", 23.129, 113.264, 95, 29, "高铁29分钟 起", "¥74 起", 74,
                        Arrays.asList("广州塔", "沙面", "上下九步行街"), Arrays.asList("美食", "历史"),
                        "早茶文化、岭南骑楼与小蛮腰夜景"),
                city("惠州", "广东", 23.112, 114.416, 82, 30, "高铁30分钟 起", "¥49 起", 49,
                        Arrays.asList("巽寮湾", "罗浮山", "双月湾"), Arrays.asList("海滨", "休闲"),
                        "巽寮湾细软沙滩，悠闲海滨度假"),
                city("东莞", "广东", 23.020, 113.752, 74, 17, "高铁17分钟 起", "¥39 起", 39,
                        Arrays.asList("松山湖", "可园", "观音山"), Arrays.asList("文化", "美食"),
                        "世界工厂的另一面：松山湖与岭南园林"),
                city("佛山", "广东", 23.021, 113.122, 80, 37, "高铁37分钟 起", "¥53 起", 53,
                        Arrays.asList("祖庙", "岭南天地", "西樵山"), Arrays.asList("美食", "武术"),
                        "功夫之乡，顺德菜美食冠绝"),
                city("珠海", "广东", 22.271, 113.577, 86, 56, "高铁56分钟 起", "¥72 起", 72,
                        Arrays.asList("珠海长隆", "情侣路", "东澳岛"), Arrays.asList("海滨", "亲子"),
                        "情侣路海风与长隆海洋王国"),
                city("中山", "广东", 22.517, 113.393, 71, 28, "高铁28分钟 起", "¥50 起", 50,
                        Arrays.asList("孙中山故居", "孙文西路", "崖口村"), Arrays.asList("华侨", "美食"),
                        "伟人故里，岭南侨乡风情"),
                city("汕头", "广东", 23.353, 116.682, 78, 90, "高铁1.5小时 起", "¥130 起", 130,
                        Arrays.asList("南澳岛", "小公园", "潮汕美食"), Arrays.asList("海滨", "美食"),
                        "潮汕美食天堂，南澳岛海景"),
                city("厦门", "福建", 24.480, 118.089, 89, 120, "高铁2小时 起", "¥170 起", 170,
                        Arrays.asList("鼓浪屿", "环岛路", "曾厝垵"), Arrays.asList("海岛", "文艺"),
                        "鼓浪屿文艺小巷与环岛海景"),
                city("长沙", "湖南", 28.228, 112.939, 87, 150, "高铁2.5小时 起", "¥210 起", 210,
                        Arrays.asList("橘子洲", "岳麓山", "太平老街"), Arrays.asList("美食", "娱乐"),
                        "嗦粉小龙虾，橘子洲头烟花"),
                city("桂林", "广西", 25.281, 110.290, 90, 180, "高铁3小时 起", "¥240 起", 240,
                        Arrays.asList("漓江", "阳朔西街", "象鼻山"), Arrays.asList("山水", "自然"),
                        "漓江山水甲天下，阳朔骑行田园"),
                city("南宁", "广西", 22.817, 108.366, 76, 190, "高铁3小时+ 起", "¥230 起", 230,
                        Arrays.asList("青秀山", "三街两巷", "邕江"), Arrays.asList("绿城", "美食"),
                        "绿城骑楼与老友粉"),
                city("贵阳", "贵州", 26.647, 106.630, 74, 240, "高铁4小时 起", "¥300 起", 300,
                        Arrays.asList("黄果树瀑布", "青岩古镇", "甲秀楼"), Arrays.asList("山地", "避暑"),
                        "避暑之都，黄果树瀑布壮景"),
                city("昆明", "云南", 24.880, 102.833, 79, 300, "高铁5小时 起", "¥360 起", 360,
                        Arrays.asList("滇池", "石林", "翠湖公园"), Arrays.asList("四季如春", "民族"),
                        "春城昆明，滇池海鸥与石林奇观")
        ));

        SURROUND.put("北京", List.of(
                city("天津", "天津", 39.084, 117.201, 84, 29, "高铁29分钟 起", "¥55 起", 55,
                        Arrays.asList("古文化街", "五大道", "海河"), Arrays.asList("津味", "欧式"),
                        "津味早点与五大道小洋楼"),
                city("承德", "河北", 40.951, 117.939, 74, 39, "高铁39分钟 起", "¥70 起", 70,
                        Arrays.asList("避暑山庄", "外八庙", "普陀宗乘寺"), Arrays.asList("皇家", "避暑"),
                        "皇家避暑胜地，外八庙群"),
                city("张家口", "河北", 40.824, 114.886, 68, 50, "高铁50分钟 起", "¥60 起", 60,
                        Arrays.asList("草原天路", "崇礼", "暖泉古镇"), Arrays.asList("草原", "冬奥"),
                        "冬奥小城崇礼，草原天路自驾"),
                city("保定", "河北", 38.874, 115.464, 72, 30, "高铁30分钟 起", "¥47 起", 47,
                        Arrays.asList("野三坡", "直隶总督署", "古莲花池"), Arrays.asList("历史", "美食"),
                        "直隶故地，野三坡峡谷"),
                city("唐山", "河北", 39.630, 118.180, 66, 47, "高铁47分钟 起", "¥54 起", 54,
                        Arrays.asList("南湖", "开滦矿山公园", "菩提岛"), Arrays.asList("工业", "海滨"),
                        "老工业城的新生，南湖夜景"),
                city("石家庄", "河北", 38.043, 114.514, 70, 75, "高铁1小时 起", "¥82 起", 82,
                        Arrays.asList("正定古城", "赵州桥", "西柏坡"), Arrays.asList("省会", "美食"),
                        "正定古城塔寺，赵州古桥"),
                city("大同", "山西", 40.090, 113.300, 72, 105, "高铁1.7小时 起", "¥120 起", 120,
                        Arrays.asList("云冈石窟", "悬空寺", "华严寺"), Arrays.asList("石窟", "古建"),
                        "云冈石窟世界遗产，悬空寺奇观"),
                city("太原", "山西", 37.870, 112.549, 71, 120, "高铁2小时 起", "¥150 起", 150,
                        Arrays.asList("晋祠", "平遥古城", "双塔寺"), Arrays.asList("面食", "古建"),
                        "晋祠泉水与平遥古城墙")
        ));

        ROUTES.add(route("r1", "越南7日自驾游", "2058公里", 7, 6,
                Arrays.asList("胡志明", "美奈", "大叻", "芽庄", "会安", "岘港"),
                Arrays.asList("胡志明", "大叻", "芽庄"), "穿越南北越南，海滨与高原一次玩遍"));
        ROUTES.add(route("r2", "粤东美食环线", "680公里", 3, 4,
                Arrays.asList("深圳", "汕头", "潮州", "揭阳"),
                Arrays.asList("汕头", "潮州"), "潮汕牛肉火锅与茶文化寻味之旅"));
        ROUTES.add(route("r3", "桂林阳朔3日", "320公里", 3, 3,
                Arrays.asList("桂林", "阳朔", "兴坪"),
                Arrays.asList("桂林", "阳朔"), "漓江竹筏漂流，阳朔十里画廊"));
        ROUTES.add(route("r4", "粤港澳经典走透", "260公里", 4, 4,
                Arrays.asList("深圳", "香港", "澳门", "广州"),
                Arrays.asList("香港", "澳门", "广州"), "一地两检穿梭粤港澳，维港大三巴全打卡"));
        ROUTES.add(route("r5", "厦门漳州双城", "150公里", 3, 3,
                Arrays.asList("厦门", "漳州", "泉州"),
                Arrays.asList("厦门", "泉州"), "海风岛屿与古港文化慢游"));
        ROUTES.add(route("r6", "西北大环线", "2000公里", 7, 6,
                Arrays.asList("西宁", "青海湖", "茶卡", "敦煌", "张掖", "嘉峪关"),
                Arrays.asList("青海湖", "敦煌", "张掖"), "沙漠湖泊与七彩丹霞的视觉盛宴"));
    }

    public SurroundTourVO getSurroundTour() {
        SurroundTourVO vo = new SurroundTourVO();
        vo.setDefaultDeparture(DEFAULT_DEPARTURE);
        vo.setDepartureCities(DEPARTURE_CITIES);
        vo.setSurround(SURROUND);
        vo.setRoutes(ROUTES);
        return vo;
    }

    /* ==================== 组装辅助 ==================== */

    private static CityPointDTO point(String name, double lat, double lng) {
        return new CityPointDTO(name, lat, lng);
    }

    private static SurroundCityDTO city(String name, String province, double lat, double lng, int heat,
                                        int transitMin, String transitLabel, String price, int priceValue,
                                        List<String> hotSpots, List<String> tags, String description) {
        SurroundCityDTO c = new SurroundCityDTO();
        c.setName(name);
        c.setProvince(province);
        c.setLat(lat);
        c.setLng(lng);
        c.setHeat(heat);
        c.setTransitMin(transitMin);
        c.setTransitLabel(transitLabel);
        c.setPrice(price);
        c.setPriceValue(priceValue);
        c.setHotSpots(hotSpots);
        c.setTags(tags);
        c.setDescription(description);
        return c;
    }

    private static RouteCardDTO route(String id, String name, String km, int days, int cityCount,
                                      List<String> cities, List<String> covers, String tagline) {
        RouteCardDTO r = new RouteCardDTO();
        r.setId(id);
        r.setName(name);
        r.setKm(km);
        r.setDays(days);
        r.setCityCount(cityCount);
        r.setCities(cities);
        r.setCovers(covers);
        r.setTagline(tagline);
        return r;
    }
}
