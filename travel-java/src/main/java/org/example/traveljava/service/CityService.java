package org.example.traveljava.service;

import org.example.traveljava.config.CacheConfig;
import org.example.traveljava.config.MapConfig;
import org.example.traveljava.dto.CityDTO.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 城市数据服务 — 境内/境外城市列表、模糊搜索、定位解析
 */
@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);
    private final RestTemplate restTemplate;
    private final MapConfig mapConfig;
    /** 【新功能】城市图片缓存迁移到统一 Caffeine 缓存 city-image（2000 条 / 1 小时，见 CacheConfig） */
    private final Cache imageCache;

    public CityService(RestTemplate restTemplate, MapConfig mapConfig, CacheManager cacheManager) {
        this.restTemplate = restTemplate;
        this.mapConfig = mapConfig;
        this.imageCache = cacheManager.getCache(CacheConfig.CACHE_CITY_IMAGE);
    }

    /**
     * 获取城市真实照片 URL
     * 优先 Flickr → 降级 Picsum
     */
    @SuppressWarnings("unchecked")
    public String fetchCityImage(String cityName) {
        // 查缓存
        Cache.ValueWrapper cached = imageCache.get(cityName);
        if (cached != null) {
            return (String) cached.get();
        }

        // 1) 尝试 Flickr 公开 Feed（免费，无需 Key）
        try {
            // RestTemplate 双重编码坑修复：必须传 URI 对象（cityName 为中文，String 传法会二次编码 %→%25）
            java.net.URI flickrUri = UriComponentsBuilder.fromHttpUrl("https://api.flickr.com/services/feeds/photos_public.gne")
                .queryParam("tags", cityName + ",travel,landmark")
                .queryParam("format", "json")
                .queryParam("nojsoncallback", 1)
                .queryParam("per_page", 1)
                .build().encode().toUri();
            Map<String, Object> resp = restTemplate.getForObject(flickrUri, Map.class);
            if (resp != null && resp.containsKey("items")) {
                List<Map<String, Object>> items = (List<Map<String, Object>>) resp.get("items");
                if (items != null && !items.isEmpty()) {
                    Map<String, Object> media = (Map<String, Object>) items.get(0).get("media");
                    if (media != null && media.containsKey("m")) {
                        String imgUrl = (String) media.get("m");
                        // 替换为更大的尺寸
                        imgUrl = imgUrl.replace("_m.jpg", "_b.jpg");
                        imageCache.put(cityName, imgUrl);
                        log.debug("Flickr图片获取成功: {} → {}", cityName, imgUrl);
                        return imgUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Flickr获取失败，降级Picsum: {}", cityName);
        }

        // 2) 无真实照片：返回空串，由前端走本地静态图兜底（不再用 picsum 随机图）
        String fallback = "";
        imageCache.put(cityName, fallback);
        return fallback;
    }

    // ==================== 境内数据 ====================

    private static final List<String> HOT_DOMESTIC = List.of(
        "北京","上海","广州","深圳","成都","杭州","重庆","武汉",
        "西安","南京","长沙","厦门","青岛","大连","三亚","昆明",
        "苏州","天津","郑州","福州","合肥","贵阳","哈尔滨","沈阳"
    );

    private static final List<String> HOT_OVERSEAS = List.of(
        "东京","大阪","首尔","曼谷","巴黎","伦敦","纽约","新加坡",
        "巴厘岛","迪拜","罗马","巴塞罗那","悉尼","普吉岛","清迈","吉隆坡",
        "马尔代夫","米兰","洛杉矶","墨尔本","苏黎世","布拉格","圣托里尼","冰岛"
    );

    // ==================== 公开接口 ====================

    /** 全部热门城市名（境内+境外），供推荐/热榜等复用 */
    public List<String> getHotCityNames() {
        List<String> all = new ArrayList<>(HOT_DOMESTIC);
        all.addAll(HOT_OVERSEAS);
        return all;
    }

    /** 【ABUSE-1】全部已知城市名白名单（境内+境外），与城市列表数据同源，一次性构建后不可变 */
    private volatile Set<String> knownCities;

    private Set<String> getAllCityNames() {
        Set<String> result = knownCities;
        if (result == null) {
            synchronized (this) {
                result = knownCities;
                if (result == null) {
                    Set<String> all = new HashSet<>(HOT_DOMESTIC);
                    all.addAll(HOT_OVERSEAS);
                    for (ProvinceGroup pg : buildAllProvinces()) {
                        all.add(pg.getLabel());
                        for (CityItem item : pg.getItems()) {
                            all.add(item.getName());
                            for (CityCard card : item.getCities()) all.add(card.getName());
                        }
                    }
                    for (CityItem item : buildHkMacauTW().getItems()) {
                        all.add(item.getName());
                        for (CityCard card : item.getCities()) all.add(card.getName());
                    }
                    for (ContinentGroup continent : buildContinents()) {
                        for (CountryGroup country : continent.getCountries()) {
                            all.add(country.getName());
                            for (CityCard card : country.getCities()) all.add(card.getName());
                        }
                    }
                    result = Collections.unmodifiableSet(all);
                    knownCities = result;
                }
            }
        }
        return knownCities;
    }

    /** 【ABUSE-1】入参白名单校验：name 是否真实城市名。未知名称不得触发外部调用/落库。 */
    public boolean isKnownCity(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return getAllCityNames().contains(name.trim());
    }

    /** 获取境内城市数据 */
    public DomesticResponse getDomesticCities() {
        List<ProvinceGroup> groups = List.of(
            buildMunicipalities(),
            buildHkMacauTW()
        );
        List<ProvinceGroup> provinces = buildAllProvinces();
        return new DomesticResponse(HOT_DOMESTIC, groups, provinces);
    }

    /** 获取境外城市数据 */
    public OverseasResponse getOverseasCities() {
        return new OverseasResponse(HOT_OVERSEAS, buildContinents());
    }

    /** 模糊搜索城市 */
    public List<SearchResult> searchCities(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();
        String kw = keyword.trim().toLowerCase();
        List<SearchResult> results = new ArrayList<>();

        // 搜境内
        searchInDomestic(kw, results);
        // 搜境外
        searchInOverseas(kw, results);

        return results.stream().limit(20).collect(Collectors.toList());
    }

    /** 定位解析 — 优先高德逆地理编码，其次百度，兜底返回空 */
    @SuppressWarnings("unchecked")
    public LocationResult reverseGeocode(double lat, double lng) {
        // 1) 优先尝试高德
        String amapKey = mapConfig.getAmap().getWebKey();
        if (amapKey != null && !amapKey.isEmpty()) {
            try {
                LocationResult result = reverseGeocodeAmap(lat, lng, amapKey);
                if (result != null && !result.getCity().isEmpty()) return result;
            } catch (Exception e) {
                log.warn("高德逆地理编码失败，尝试百度: {}", e.getMessage());
            }
        }

        // 2) 降级百度
        String baiduAk = mapConfig.getBaidu().getAk();
        if (baiduAk != null && !baiduAk.isEmpty()) {
            try {
                LocationResult result = reverseGeocodeBaidu(lat, lng, baiduAk);
                if (result != null && !result.getCity().isEmpty()) return result;
            } catch (Exception e) {
                log.warn("百度逆地理编码失败: {}", e.getMessage());
            }
        }

        log.warn("地图Key均未配置或逆地理编码均失败，返回空定位");
        return new LocationResult("", "", lat, lng);
    }

    /** 高德逆地理编码：https://restapi.amap.com/v3/geocode/regeo */
    @SuppressWarnings("unchecked")
    private LocationResult reverseGeocodeAmap(double lat, double lng, String key) {
        // RestTemplate 双重编码坑修复：必须传 URI 对象（location 含逗号，String 传法可能被误编码）
        java.net.URI uri = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/geocode/regeo")
            .queryParam("key", key)
            .queryParam("location", String.format("%f,%f", lng, lat))
            .queryParam("output", "JSON")
            .build().encode().toUri();
        Map<String, Object> resp = restTemplate.getForObject(uri, Map.class);
        if (resp == null || !"1".equals(String.valueOf(resp.getOrDefault("status", "")))) {
            return null;
        }
        Map<String, Object> regeo = (Map<String, Object>) resp.get("regeocode");
        if (regeo == null) return null;

        String formattedAddr = (String) regeo.getOrDefault("formatted_address", "");
        Map<String, Object> addrComp = (Map<String, Object>) regeo.get("addressComponent");
        if (addrComp == null) return null;

        String province = "", city = "", district = "", street = "";
        // 高德 province 可能是数组（如 ["北京市"]），也可能是字符串
        Object provObj = addrComp.getOrDefault("province", "");
        province = provObj instanceof List ? String.join("", (List<String>) provObj) : String.valueOf(provObj);
        Object cityObj = addrComp.getOrDefault("city", "");
        city = cityObj instanceof List ? String.join("", (List<String>) cityObj) : String.valueOf(cityObj);
        Object distObj = addrComp.getOrDefault("district", "");
        district = distObj instanceof List ? String.join("", (List<String>) distObj) : String.valueOf(distObj);

        Map<String, Object> streetNumber = (Map<String, Object>) addrComp.get("streetNumber");
        if (streetNumber != null) {
            street = String.valueOf(streetNumber.getOrDefault("street", ""));
        }

        // 直辖市 city 可能为空，用 province 代替
        if (city.isEmpty() || "[]".equals(city)) city = province;

        String fullAddress = formattedAddr.isEmpty()
            ? (province + city + district + street).replace("null", "").replace("[]", "")
            : formattedAddr;

        LocationResult lr = new LocationResult(city, province, lat, lng);
        lr.setDistrict(district);
        lr.setStreet(street);
        lr.setAddress(fullAddress);
        return lr;
    }

    /** 百度逆地理编码：https://api.map.baidu.com/reverse_geocoding/v3/ */
    @SuppressWarnings("unchecked")
    private LocationResult reverseGeocodeBaidu(double lat, double lng, String ak) {
        // RestTemplate 双重编码坑修复：必须传 URI 对象
        java.net.URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/reverse_geocoding/v3/")
            .queryParam("ak", ak)
            .queryParam("output", "json")
            .queryParam("coordtype", "wgs84ll")
            .queryParam("location", String.format("%f,%f", lat, lng))
            .build().encode().toUri();
        Map<String, Object> resp = restTemplate.getForObject(uri, Map.class);
        if (resp != null && (int) resp.getOrDefault("status", -1) == 0) {
            Map<String, Object> result = (Map<String, Object>) resp.get("result");
            if (result != null) {
                String formattedAddr = (String) result.getOrDefault("formatted_address", "");
                Map<String, Object> addressComp = (Map<String, Object>) result.get("addressComponent");
                if (addressComp != null) {
                    String city = (String) addressComp.getOrDefault("city", "");
                    String province = (String) addressComp.getOrDefault("province", "");
                    String district = (String) addressComp.getOrDefault("district", "");
                    String street = (String) addressComp.getOrDefault("street", "");
                    if (city.isEmpty()) city = province;
                    String fullAddress = formattedAddr.isEmpty()
                        ? (province + city + district + street).replace("null", "")
                        : formattedAddr;
                    LocationResult lr = new LocationResult(city, province, lat, lng);
                    lr.setDistrict(district);
                    lr.setStreet(street);
                    lr.setAddress(fullAddress);
                    return lr;
                }
            }
        }
        log.warn("百度逆地理编码失败: {}", resp);
        return null;
    }

    // ==================== 境内数据构建 ====================

    private ProvinceGroup buildMunicipalities() {
        List<CityItem> items = List.of(
            new CityItem("北京", toCards("东城区","西城区","朝阳区","海淀区","丰台区","石景山区","通州区","大兴区","顺义区","昌平区","房山区","门头沟区","怀柔区","平谷区","密云区","延庆区")),
            new CityItem("上海", toCards("浦东新区","黄浦区","徐汇区","长宁区","静安区","虹口区","杨浦区","闵行区","普陀区","宝山区","嘉定区","松江区","青浦区","奉贤区","金山区","崇明区")),
            new CityItem("天津", toCards("和平区","河东区","河西区","南开区","河北区","红桥区","滨海新区","武清区","宝坻区","东丽区","西青区","津南区","北辰区","静海区","宁河区","蓟州区")),
            new CityItem("重庆", toCards("渝中区","江北区","南岸区","沙坪坝区","九龙坡区","渝北区","巴南区","北碚区","大渡口区","涪陵区","万州区","黔江区","长寿区","江津区","合川区","永川区"))
        );
        return new ProvinceGroup("municipalities", "直辖市", "group", items);
    }

    private ProvinceGroup buildHkMacauTW() {
        List<CityItem> items = new ArrayList<>();
        items.add(new CityItem("香港", toCards("中西区","湾仔区","东区","南区","油尖旺区","深水埗区","九龙城区","黄大仙区","观塘区","荃湾区","屯门区","元朗区","北区","大埔区","沙田区","西贡区","离岛区","葵青区")));
        items.add(new CityItem("澳门", toCards("花地玛堂区","圣安多尼堂区","大堂区","望德堂区","风顺堂区","嘉模堂区","路氹填海区","圣方济各堂区")));
        // 台湾各市县
        String[][] twCities = {
            {"台北", "中正区,大同区,中山区,松山区,大安区,万华区,信义区,士林区,北投区,内湖区,南港区,文山区,淡水,九份,阳明山"},
            {"新北", "板桥区,新庄区,中和区,永和区,土城区,树林区,三峡区,莺歌区,三重区,芦洲区,五股区,金山区,野柳"},
            {"高雄", "苓雅区,前镇区,盐埕区,鼓山区,旗津区,左营区,楠梓区,三民区,新兴区,前金区,凤山区,小港区,美浓"},
            {"台中", "中区,东区,南区,西区,北区,西屯区,南屯区,北屯区,丰原区,大甲区,清水区,雾峰区,逢甲,高美湿地"},
            {"台南", "中西区,东区,南区,北区,安平区,安南区,永康区,仁德区,七股,关子岭"},
            {"桃园", "桃园区,中坜区,平镇区,八德区,杨梅区,芦竹区,大溪区,龙潭区,大园区,复兴区"},
            {"花莲", "花莲市,吉安乡,寿丰乡,凤林镇,光复乡,瑞穗乡,玉里镇,太鲁阁,七星潭,清水断崖"},
            {"台东", "台东市,成功镇,关山镇,卑南乡,池上乡,鹿野乡,绿岛,兰屿,知本"},
            {"宜兰", "宜兰市,罗东镇,苏澳镇,头城镇,礁溪乡,壮围乡,冬山乡,三星乡,太平山"},
            {"南投", "南投市,埔里镇,草屯镇,竹山镇,集集镇,日月潭,清境农场,溪头,合欢山"},
            {"嘉义", "东区,西区,朴子市,布袋镇,阿里山,奋起湖"},
            {"屏东", "屏东市,潮州镇,东港镇,恒春镇,垦丁,小琉球,雾台,枋寮"},
            {"苗栗", "苗栗市,头份市,竹南镇,苑里镇,三义乡,南庄,大湖,泰安"},
            {"新竹", "东区,北区,香山区,竹北市,新丰乡,湖口乡,关西镇,内湾,司马库斯"},
            {"彰化", "彰化市,员林市,鹿港镇,和美镇,北斗镇,田中镇,芳苑乡,八卦山"},
            {"云林", "斗六市,虎尾镇,西螺镇,北港镇,古坑乡,草岭"},
            {"澎湖", "马公市,湖西乡,白沙乡,西屿乡,七美,望安,吉贝,双心石沪"},
            {"金门", "金城镇,金湖镇,金沙镇,金宁乡,烈屿乡"},
            {"马祖", "南竿乡,北竿乡,莒光乡,东引乡"},
            {"基隆", "中正区,信义区,仁爱区,中山区,安乐区,暖暖区,七堵区,和平岛"},
        };
        for (String[] tw : twCities) {
            items.add(new CityItem(tw[0], toCards(tw[1].split(","))));
        }
        return new ProvinceGroup("hk_macau_tw", "港澳台", "group", items);
    }

    private List<ProvinceGroup> buildAllProvinces() {
        List<ProvinceGroup> list = new ArrayList<>();
        String[][] data = {
            {"广东","广州,深圳,珠海,东莞,佛山,中山,惠州,汕头,江门,湛江,茂名,肇庆,梅州,汕尾,河源,阳江,清远,潮州,揭阳,云浮,韶关"},
            {"浙江","杭州,宁波,温州,嘉兴,湖州,绍兴,金华,舟山,衢州,台州,丽水"},
            {"江苏","南京,苏州,无锡,常州,南通,扬州,镇江,徐州,盐城,泰州,淮安,连云港,宿迁"},
            {"四川","成都,绵阳,德阳,宜宾,南充,泸州,乐山,眉山,自贡,攀枝花,广元,遂宁,内江,广安,达州,雅安,巴中,资阳,阿坝,甘孜,凉山"},
            {"湖北","武汉,宜昌,襄阳,荆州,黄石,十堰,鄂州,孝感,黄冈,咸宁,随州,恩施,荆门,仙桃,天门,潜江,神农架"},
            {"湖南","长沙,株洲,湘潭,衡阳,岳阳,常德,张家界,郴州,益阳,永州,怀化,娄底,邵阳,湘西,吉首,凤凰"},
            {"福建","福州,厦门,泉州,漳州,莆田,龙岩,三明,南平,宁德,武夷山,平潭"},
            {"山东","济南,青岛,烟台,威海,潍坊,淄博,临沂,济宁,泰安,日照,德州,聊城,滨州,菏泽,枣庄,东营,曲阜"},
            {"河南","郑州,洛阳,开封,南阳,许昌,新乡,安阳,信阳,商丘,周口,驻马店,平顶山,焦作,濮阳,漯河,三门峡,鹤壁,济源"},
            {"河北","石家庄,唐山,保定,邯郸,廊坊,沧州,秦皇岛,张家口,承德,邢台,衡水,雄安新区"},
            {"辽宁","沈阳,大连,鞍山,抚顺,本溪,丹东,锦州,营口,阜新,辽阳,盘锦,铁岭,朝阳,葫芦岛"},
            {"陕西","西安,咸阳,宝鸡,渭南,延安,汉中,榆林,安康,商洛,铜川,华山"},
            {"云南","昆明,大理,丽江,香格里拉,西双版纳,腾冲,普洱,曲靖,玉溪,保山,昭通,临沧,楚雄,红河,文山,德宏,怒江,迪庆"},
            {"贵州","贵阳,遵义,安顺,毕节,铜仁,黔东南,黔南,黔西南,六盘水,凯里,镇远,荔波,黄果树,梵净山"},
            {"广西","南宁,桂林,柳州,北海,玉林,梧州,防城港,钦州,贵港,百色,贺州,河池,来宾,崇左,阳朔,涠洲岛"},
            {"海南","海口,三亚,儋州,琼海,文昌,万宁,五指山,东方,陵水,乐东,澄迈,临高,定安,屯昌,昌江,保亭,琼中,三沙"},
            {"安徽","合肥,芜湖,蚌埠,淮南,马鞍山,安庆,黄山,阜阳,宿州,滁州,六安,宣城,池州,亳州,铜陵,淮北,九华山"},
            {"江西","南昌,九江,景德镇,赣州,上饶,宜春,吉安,抚州,萍乡,新余,鹰潭,庐山,婺源,井冈山,三清山"},
            {"山西","太原,大同,阳泉,长治,晋城,临汾,运城,吕梁,晋中,忻州,朔州,平遥,五台山,壶口瀑布"},
            {"吉林","长春,吉林,四平,辽源,通化,白山,延边,松原,白城,长白山"},
            {"黑龙江","哈尔滨,齐齐哈尔,牡丹江,佳木斯,大庆,鸡西,鹤岗,黑河,双鸭山,伊春,七台河,绥化,大兴安岭,漠河,雪乡,亚布力"},
            {"甘肃","兰州,嘉峪关,天水,武威,张掖,酒泉,敦煌,平凉,金昌,白银,定西,陇南,庆阳,临夏,甘南"},
            {"内蒙古","呼和浩特,包头,鄂尔多斯,呼伦贝尔,赤峰,通辽,乌海,巴彦淖尔,乌兰察布,兴安盟,锡林郭勒,阿拉善,满洲里,阿尔山,额济纳"},
            {"新疆","乌鲁木齐,克拉玛依,吐鲁番,哈密,喀什,伊犁,阿勒泰,库尔勒,阿克苏,和田,昌吉,博尔塔拉,巴音郭楞,克孜勒苏,塔城,喀纳斯,那拉提,赛里木湖"},
            {"西藏","拉萨,日喀则,林芝,山南,那曲,昌都,阿里,珠峰大本营,纳木错,羊卓雍措,雅鲁藏布大峡谷"},
            {"青海","西宁,海东,格尔木,德令哈,玉树,果洛,海北,黄南,海南,海西,青海湖,茶卡盐湖,祁连"},
            {"宁夏","银川,石嘴山,吴忠,固原,中卫,沙坡头,贺兰山"},
        };
        for (String[] d : data) {
            List<CityItem> items = List.of(
                new CityItem(d[0], toCards(d[1].split(",")))
            );
            list.add(new ProvinceGroup(d[0], d[0], "province", items));
        }
        return list;
    }

    // ==================== 境外数据构建 ====================

    private List<ContinentGroup> buildContinents() {
        return List.of(
            new ContinentGroup("亚洲", List.of(
                c("日本","东京,大阪,京都,札幌,福冈,名古屋,神户,奈良,冲绳,横滨,箱根,镰仓,富士山,小樽,函馆"),
                c("韩国","首尔,釜山,济州,仁川,大邱,光州,大田,蔚山,江原道,庆州,全州,水原"),
                c("泰国","曼谷,清迈,普吉岛,芭提雅,苏梅岛,甲米,华欣,大城,清莱,拜县,素可泰,象岛,丽贝岛,斯米兰,皮皮岛"),
                c("新加坡","新加坡,圣淘沙,裕廊,樟宜,乌节路,牛车水,小印度,克拉码头,滨海湾"),
                c("马来西亚","吉隆坡,槟城,马六甲,兰卡威,沙巴,新山,怡保,古晋,仙本那,热浪岛,停泊岛"),
                c("越南","河内,胡志明市,岘港,芽庄,会安,大叻,富国岛,下龙湾,美奈,顺化,沙坝"),
                c("印度尼西亚","巴厘岛,雅加达,日惹,龙目岛,万隆,泗水,棉兰,美娜多,科莫多岛,民丹岛"),
                c("菲律宾","马尼拉,长滩岛,宿务,薄荷岛,巴拉望,杜马盖地,锡亚高,科隆,爱妮岛"),
                c("阿联酋","迪拜,阿布扎比,沙迦,拉斯海玛"),
                c("印度","新德里,孟买,阿格拉,斋浦尔,班加罗尔,果阿,金奈,加尔各答,瓦拉纳西"),
                c("柬埔寨","金边,暹粒,西哈努克,马德望,贡布"),
                c("马尔代夫","马累,胡鲁马累,阿杜市,北马累环礁,南马累环礁,阿里环礁"),
                c("斯里兰卡","科伦坡,康提,加勒,努沃勒埃利耶,锡吉里耶,丹布拉,美蕊沙"),
                c("缅甸","仰光,曼德勒,蒲甘,茵莱湖"),
                c("老挝","万象,琅勃拉邦,万荣,巴色"),
                c("尼泊尔","加德满都,博卡拉,奇特旺,蓝毗尼"),
                c("土耳其","伊斯坦布尔,卡帕多奇亚,安塔利亚,伊兹密尔,安卡拉,费特希耶,棉花堡"),
                c("以色列","耶路撒冷,特拉维夫,海法,死海"),
                c("约旦","安曼,佩特拉,死海,瓦迪拉姆,亚喀巴")
            )),
            new ContinentGroup("欧洲", List.of(
                c("法国","巴黎,尼斯,里昂,马赛,波尔多,戛纳,斯特拉斯堡,阿维尼翁,科尔马,普罗旺斯,霞慕尼"),
                c("意大利","罗马,威尼斯,米兰,佛罗伦萨,那不勒斯,比萨,博洛尼亚,维罗纳,五渔村,阿马尔菲,科莫湖,多洛米蒂"),
                c("英国","伦敦,爱丁堡,曼彻斯特,利物浦,牛津,剑桥,巴斯,约克,格拉斯哥,天空岛,巨石阵,科茨沃尔德"),
                c("德国","柏林,慕尼黑,法兰克福,汉堡,科隆,海德堡,斯图加特,纽伦堡,德累斯顿,新天鹅堡,国王湖,黑森林"),
                c("西班牙","巴塞罗那,马德里,塞维利亚,格拉纳达,瓦伦西亚,马拉加,毕尔巴鄂,托莱多,龙达,马略卡岛"),
                c("瑞士","苏黎世,日内瓦,卢塞恩,因特拉肯,伯尔尼,洛桑,采尔马特,少女峰,马特洪峰,蒙特勒,格林德瓦"),
                c("希腊","雅典,圣托里尼,米克诺斯,克里特,罗德岛,扎金索斯,塞萨洛尼基,梅黛奥拉,德尔斐"),
                c("荷兰","阿姆斯特丹,鹿特丹,海牙,乌得勒支,代尔夫特,羊角村,风车村,库肯霍夫"),
                c("捷克","布拉格,克鲁姆洛夫,布尔诺,卡罗维发利"),
                c("奥地利","维也纳,萨尔茨堡,哈尔施塔特,因斯布鲁克,格拉茨,圣沃尔夫冈"),
                c("冰岛","雷克雅未克,阿克雷里,维克,赫本,蓝湖,黄金圈,冰河湖,斯奈山半岛"),
                c("挪威","奥斯陆,卑尔根,特罗姆瑟,斯塔万格,弗洛姆,罗弗敦群岛,松恩峡湾"),
                c("瑞典","斯德哥尔摩,哥德堡,马尔默,基律纳"),
                c("芬兰","赫尔辛基,罗瓦涅米,图尔库,圣诞老人村,拉普兰"),
                c("丹麦","哥本哈根,奥胡斯,欧登塞,比隆,法罗群岛"),
                c("葡萄牙","里斯本,波尔图,辛特拉,法鲁,马德拉群岛"),
                c("匈牙利","布达佩斯,埃格尔,佩奇,巴拉顿湖"),
                c("克罗地亚","杜布罗夫尼克,萨格勒布,斯普利特,扎达尔,十六湖,赫瓦尔岛"),
                c("波兰","华沙,克拉科夫,格但斯克,弗罗茨瓦夫"),
                c("爱尔兰","都柏林,戈尔韦,科克,莫赫悬崖"),
                c("比利时","布鲁塞尔,布鲁日,根特,安特卫普"),
                c("俄罗斯","莫斯科,圣彼得堡,喀山,索契,伊尔库茨克,贝加尔湖,摩尔曼斯克")
            )),
            new ContinentGroup("北美洲", List.of(
                c("美国","纽约,洛杉矶,旧金山,拉斯维加斯,芝加哥,华盛顿,波士顿,西雅图,迈阿密,奥兰多,圣地亚哥,费城,夏威夷,黄石公园,大峡谷,优胜美地,阿拉斯加"),
                c("加拿大","多伦多,温哥华,蒙特利尔,渥太华,卡尔加里,魁北克,班夫,维多利亚,贾斯珀,惠斯勒,尼亚加拉瀑布"),
                c("墨西哥","墨西哥城,坎昆,瓜达拉哈拉,洛斯卡沃斯,图卢姆,梅里达,瓜纳华托"),
                c("古巴","哈瓦那,巴拉德罗,特立尼达,西恩富戈斯")
            )),
            new ContinentGroup("南美洲", List.of(
                c("巴西","里约热内卢,圣保罗,萨尔瓦多,巴西利亚,马瑙斯,伊瓜苏"),
                c("阿根廷","布宜诺斯艾利斯,乌斯怀亚,巴里洛切,门多萨,埃尔卡拉法特,伊瓜苏港"),
                c("秘鲁","利马,库斯科,马丘比丘,阿雷基帕,普诺,纳斯卡"),
                c("智利","圣地亚哥,复活节岛,瓦尔帕莱索,阿塔卡马,百内国家公园"),
                c("哥伦比亚","波哥大,卡塔赫纳,麦德林,卡利"),
                c("厄瓜多尔","基多,加拉帕戈斯群岛,瓜亚基尔,昆卡"),
                c("玻利维亚","拉巴斯,乌尤尼盐沼,苏克雷")
            )),
            new ContinentGroup("大洋洲", List.of(
                c("澳大利亚","悉尼,墨尔本,黄金海岸,布里斯班,珀斯,凯恩斯,阿德莱德,霍巴特,达尔文,乌鲁鲁,大堡礁,大洋路,袋鼠岛"),
                c("新西兰","奥克兰,皇后镇,基督城,惠灵顿,罗托鲁瓦,但尼丁,米尔福德峡湾,库克山,霍比屯,蒂卡波湖,福克斯冰川"),
                c("斐济","苏瓦,楠迪,马马努卡群岛,亚萨瓦群岛"),
                c("帕劳","科罗尔,洛克群岛,水母湖,牛奶湖"),
                c("大溪地","帕皮提,波拉波拉岛,茉莉亚岛")
            )),
            new ContinentGroup("南极洲", List.of(
                c("南极","南极半岛,南设得兰群岛,罗斯海,威德尔海,麦克默多站,南乔治亚岛,福克兰群岛,德雷克海峡")
            )),
            new ContinentGroup("非洲", List.of(
                c("埃及","开罗,卢克索,阿斯旺,亚历山大,沙姆沙伊赫,赫尔格达,阿布辛贝,锡瓦绿洲,达哈卜"),
                c("摩洛哥","马拉喀什,卡萨布兰卡,菲斯,舍夫沙万,拉巴特,撒哈拉,丹吉尔,索维拉,瓦尔扎扎特,艾西拉"),
                c("南非","开普敦,约翰内斯堡,比勒陀利亚,德班,克鲁格国家公园,花园大道,赫曼努斯"),
                c("肯尼亚","内罗毕,蒙巴萨,马赛马拉,纳库鲁,安博塞利"),
                c("坦桑尼亚","达累斯萨拉姆,阿鲁沙,桑给巴尔,塞伦盖蒂,乞力马扎罗,恩戈罗恩戈罗"),
                c("毛里求斯","路易港,大湾,蓝湾,鹿岛,七色土"),
                c("塞舌尔","马埃岛,普拉兰岛,拉迪格岛"),
                c("突尼斯","突尼斯城,苏塞,哈马马特,凯鲁万,蓝白小镇"),
                c("纳米比亚","温得和克,斯瓦科普蒙德,埃托沙国家公园,苏丝斯黎"),
                c("埃塞俄比亚","亚的斯亚贝巴,拉利贝拉,阿克苏姆"),
                c("马达加斯加","塔那那利佛,诺西贝岛,穆龙达瓦")
            ))
        );
    }

    private CountryGroup c(String name, String cities) {
        return new CountryGroup(name, toCards(cities.split(",")));
    }

    // ==================== 搜索 ====================

    private void searchInDomestic(String kw, List<SearchResult> results) {
        // 搜直辖市
        for (String city : new String[]{"北京","上海","天津","重庆"}) {
            if (city.toLowerCase().contains(kw)) {
                results.add(new SearchResult("domestic", city, "直辖市"));
            }
        }
        // 搜省份 + 省份城市
        for (ProvinceGroup pg : buildAllProvinces()) {
            for (CityItem item : pg.getItems()) {
                if (item.getName().toLowerCase().contains(kw)) {
                    results.add(new SearchResult("domestic", item.getName(), pg.getLabel()));
                }
                for (CityCard card : item.getCities()) {
                    if (card.getName().toLowerCase().contains(kw)) {
                        results.add(new SearchResult("domestic", card.getName(), item.getName()));
                    }
                }
            }
        }
        // 搜港澳台
        for (CityItem item : buildHkMacauTW().getItems()) {
            if (item.getName().toLowerCase().contains(kw)) {
                results.add(new SearchResult("domestic", item.getName(), "港澳台"));
            }
            for (CityCard card : item.getCities()) {
                if (card.getName().toLowerCase().contains(kw)) {
                    results.add(new SearchResult("domestic", card.getName(), item.getName()));
                }
            }
        }
    }

    private void searchInOverseas(String kw, List<SearchResult> results) {
        for (ContinentGroup continent : buildContinents()) {
            for (CountryGroup country : continent.getCountries()) {
                if (country.getName().toLowerCase().contains(kw)) {
                    results.add(new SearchResult("overseas", country.getName(), continent.getName()));
                }
                for (CityCard card : country.getCities()) {
                    if (card.getName().toLowerCase().contains(kw)) {
                        results.add(new SearchResult("overseas", card.getName(), country.getName()));
                    }
                }
            }
        }
    }

    // ==================== 工具 ====================

    private List<CityCard> toCards(String... names) {
        return Arrays.stream(names).map(CityCard::new).collect(Collectors.toList());
    }
}
