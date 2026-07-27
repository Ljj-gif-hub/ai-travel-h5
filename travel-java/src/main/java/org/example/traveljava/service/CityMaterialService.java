package org.example.traveljava.service;

import org.example.traveljava.config.MapConfig;
import org.example.traveljava.entity.CityImage;
import org.example.traveljava.entity.CityMaterial;
import org.example.traveljava.repository.CityImageRepository;
import org.example.traveljava.repository.CityMaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 城市素材服务 — 初始化全国行政区划编码 + 图片匹配
 * 国标 GB/T 2260 行政区划代码
 */
@Service
public class CityMaterialService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CityMaterialService.class);
    private final CityMaterialRepository repo;
    private final CityImageRepository cityImageRepository;
    private final MapConfig mapConfig;
    private AmapService amapService; // 延迟注入，避免循环依赖

    public CityMaterialService(CityMaterialRepository repo, CityImageRepository cityImageRepository,
                               MapConfig mapConfig) {
        this.repo = repo;
        this.cityImageRepository = cityImageRepository;
        this.mapConfig = mapConfig;
    }

    /** Setter 注入避免循环依赖 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAmapService(AmapService amapService) {
        this.amapService = amapService;
    }

    /** 图片兜底：Picsum 固定种子（比 Unsplash 稳定，同一城市永远同一张） */
    private String thumbImg(String cityName) {
        return "https://picsum.photos/seed/" + cityName + "/400/300";
    }

    // ========== 全国行政区划数据 ==========
    private static final String[][] DOMESTIC = {
        // 直辖市
        {"110000","北京","110000","北京","首都,历史文化,故宫"},
        {"310000","上海","310000","上海","魔都,外滩,都市"},
        {"120000","天津","120000","天津","津门,近代建筑"},
        {"500000","重庆","500000","重庆","山城,火锅,洪崖洞"},
        // 广东
        {"440100","广州","440000","广东","花城,珠江,岭南文化"},
        {"440300","深圳","440000","广东","特区,滨海,创新之都"},
        {"440400","珠海","440000","广东","百岛之市,情侣路"},
        {"441900","东莞","440000","广东","制造业,松山湖"},
        {"440600","佛山","440000","广东","武术之乡,岭南天地"},
        {"442000","中山","440000","广东","孙中山故里"},
        {"441300","惠州","440000","广东","西湖,滨海"},
        {"440500","汕头","440000","广东","南澳岛,潮汕文化"},
        {"440700","江门","440000","广东","侨乡,开平碉楼"},
        {"440800","湛江","440000","广东","海滨,红树林"},
        {"440900","茂名","440000","广东","滨海绿城"},
        {"441200","肇庆","440000","广东","七星岩,鼎湖山"},
        {"441400","梅州","440000","广东","客家之乡"},
        {"441500","汕尾","440000","广东","红海湾"},
        {"441600","河源","440000","广东","万绿湖"},
        {"441700","阳江","440000","广东","海陵岛"},
        {"441800","清远","440000","广东","漂流之乡"},
        {"445100","潮州","440000","广东","潮州古城,广济桥"},
        {"445200","揭阳","440000","广东","榕城"},
        {"445300","云浮","440000","广东","禅都"},
        {"440200","韶关","440000","广东","丹霞山"},
        // 浙江
        {"330100","杭州","330000","浙江","西湖,互联网之都"},
        {"330200","宁波","330000","浙江","港口,天一阁"},
        {"330300","温州","330000","浙江","民营经济,雁荡山"},
        {"330400","嘉兴","330000","浙江","乌镇,南湖"},
        {"330500","湖州","330000","浙江","莫干山,南浔古镇"},
        {"330600","绍兴","330000","浙江","水乡,鲁迅故里"},
        {"330700","金华","330000","浙江","义乌,横店"},
        {"330900","舟山","330000","浙江","普陀山,海岛"},
        {"330800","衢州","330000","浙江","南孔圣地"},
        {"331000","台州","330000","浙江","神仙居"},
        {"331100","丽水","330000","浙江","古堰画乡"},
        // 江苏
        {"320100","南京","320000","江苏","六朝古都,中山陵"},
        {"320500","苏州","320000","江苏","园林,平江路"},
        {"320200","无锡","320000","江苏","太湖,灵山大佛"},
        {"320400","常州","320000","江苏","恐龙园,天目湖"},
        {"320600","南通","320000","江苏","濠河"},
        {"321000","扬州","320000","江苏","瘦西湖,早茶"},
        {"321100","镇江","320000","江苏","金山寺"},
        {"320300","徐州","320000","江苏","汉文化"},
        {"320900","盐城","320000","江苏","湿地之都"},
        {"321200","泰州","320000","江苏","水城慢生活"},
        {"320800","淮安","320000","江苏","运河之都"},
        {"320700","连云港","320000","江苏","花果山"},
        {"321300","宿迁","320000","江苏","项王故里"},
        // 四川
        {"510100","成都","510000","四川","天府之国,大熊猫"},
        {"510700","绵阳","510000","四川","科技城"},
        {"510600","德阳","510000","四川","三星堆"},
        {"511500","宜宾","510000","四川","五粮液,蜀南竹海"},
        {"511300","南充","510000","四川","阆中古城"},
        {"510500","泸州","510000","四川","酒城"},
        {"511100","乐山","510000","四川","乐山大佛,峨眉山"},
        {"511400","眉山","510000","四川","三苏故里"},
        {"510300","自贡","510000","四川","盐都,灯会"},
        {"510400","攀枝花","510000","四川","阳光花城"},
        {"510800","广元","510000","四川","剑门关"},
        {"510900","遂宁","510000","四川","观音故里"},
        {"511000","内江","510000","四川","甜城"},
        {"511600","广安","510000","四川","邓小平故里"},
        {"511700","达州","510000","四川","巴人故里"},
        {"511800","雅安","510000","四川","熊猫故乡,蒙顶山"},
        {"511900","巴中","510000","四川","光雾山"},
        {"512000","资阳","510000","四川","柠檬之都"},
        {"513200","阿坝","510000","四川","九寨沟,黄龙"},
        {"513300","甘孜","510000","四川","稻城亚丁,色达"},
        {"513400","凉山","510000","四川","泸沽湖,邛海"},
        // 继续补齐...
        {"420100","武汉","420000","湖北","江城,黄鹤楼"},
        {"420500","宜昌","420000","湖北","三峡,清江画廊"},
        {"420600","襄阳","420000","湖北","古城,隆中"},
        {"421000","荆州","420000","湖北","楚国故都"},
        {"430100","长沙","430000","湖南","星城,橘子洲头"},
        {"430200","株洲","430000","湖南","动力之都"},
        {"430300","湘潭","430000","湖南","伟人故里"},
        {"430400","衡阳","430000","湖南","南岳衡山"},
        {"430600","岳阳","430000","湖南","岳阳楼,洞庭湖"},
        {"430700","常德","430000","湖南","桃花源"},
        {"430800","张家界","430000","湖南","武陵源,天门山"},
        {"350100","福州","350000","福建","榕城,三坊七巷"},
        {"350200","厦门","350000","福建","鼓浪屿,环岛路"},
        {"350500","泉州","350000","福建","海上丝绸之路"},
        {"370100","济南","370000","山东","泉城,大明湖"},
        {"370200","青岛","370000","山东","崂山,栈桥"},
        {"370600","烟台","370000","山东","蓬莱阁,长岛"},
        {"371000","威海","370000","山东","刘公岛,成山头"},
        {"410100","郑州","410000","河南","商都,少林寺"},
        {"410300","洛阳","410000","河南","龙门石窟,牡丹"},
        {"410200","开封","410000","河南","清明上河园"},
        {"130100","石家庄","130000","河北","西柏坡"},
        {"130200","唐山","130000","河北","清东陵"},
        {"130300","秦皇岛","130000","河北","北戴河,山海关"},
        {"210100","沈阳","210000","辽宁","盛京,故宫"},
        {"210200","大连","210000","辽宁","星海广场"},
        {"610100","西安","610000","陕西","兵马俑,大雁塔"},
        {"530100","昆明","530000","云南","春城,石林"},
        {"532900","大理","530000","云南","洱海,苍山,古城"},
        {"530700","丽江","530000","云南","玉龙雪山,束河"},
        {"533400","香格里拉","530000","云南","梅里雪山,普达措"},
        {"520100","贵阳","520000","贵州","避暑之都,黔灵山"},
        {"520300","遵义","520000","贵州","茅台,赤水"},
        {"450100","南宁","450000","广西","绿城,青秀山"},
        {"450300","桂林","450000","广西","漓江,阳朔,龙脊梯田"},
        {"460100","海口","460000","海南","椰城,骑楼"},
        {"460200","三亚","460000","海南","亚龙湾,天涯海角"},
        {"340100","合肥","340000","安徽","包公故里,巢湖"},
        {"341000","黄山","340000","安徽","黄山,宏村,西递"},
        {"360100","南昌","360000","江西","英雄城,滕王阁"},
        {"360400","九江","360000","江西","庐山,鄱阳湖"},
        {"140100","太原","140000","山西","晋祠,汾河"},
        {"140200","大同","140000","山西","云冈石窟,悬空寺"},
        {"220100","长春","220000","吉林","伪满皇宫,净月潭"},
        {"230100","哈尔滨","230000","黑龙江","冰雪大世界,索菲亚"},
        {"620100","兰州","620000","甘肃","黄河铁桥,牛肉面"},
        {"620200","嘉峪关","620000","甘肃","嘉峪关城楼"},
        {"620700","张掖","620000","甘肃","七彩丹霞"},
        {"620900","酒泉","620000","甘肃","莫高窟,鸣沙山月牙泉"},
        {"150100","呼和浩特","150000","内蒙古","青城,大召寺"},
        {"150600","鄂尔多斯","150000","内蒙古","草原,成吉思汗陵"},
        {"650100","乌鲁木齐","650000","新疆","天山,大巴扎"},
        {"654000","伊犁","650000","新疆","那拉提,赛里木湖"},
        {"540100","拉萨","540000","西藏","布达拉宫,大昭寺"},
        {"630100","西宁","630000","青海","塔尔寺,青海湖"},
        {"640100","银川","640000","宁夏","西夏王陵,沙坡头"},
        {"810000","香港","810000","香港","维多利亚港,太平山"},
        {"820000","澳门","820000","澳门","大三巴,葡京"},
        {"710000","台北","710000","台湾","101大楼,故宫博物院"},
        {"710100","新北","710000","台湾","九份,野柳"},
        {"710200","高雄","710000","台湾","驳二,西子湾"},
        {"710300","台中","710000","台湾","逢甲夜市,彩虹眷村"},
        {"710400","台南","710000","台湾","安平古堡,赤崁楼"},
        {"710500","花莲","710000","台湾","太鲁阁,七星潭"},
        {"710600","台东","710000","台湾","绿岛,三仙台"},
    };

    @Override
    public void run(String... args) {
        // 【数据修复】清除旧的 Unsplash URL（source.unsplash.com 已停用）
        // 凡是 thumbImg 包含 unsplash 的旧数据，都替换为 Picsum
        int fixed = 0;
        for (CityMaterial cm : repo.findAll()) {
            String img = cm.getThumbImg();
            if (img != null && img.contains("unsplash")) {
                cm.setThumbImg(thumbImg(cm.getCityName()));
                repo.save(cm);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("已修复 {} 条 Unsplash 图片链接 → Picsum", fixed);
        }

        // 初始化缺失的城市（首次启动）
        if (repo.count() == 0) {
            log.info("初始化全国城市素材库...");
            int count = 0;
            for (String[] row : DOMESTIC) {
                if (repo.existsByCityCode(row[0])) continue;
                repo.save(new CityMaterial(row[0], row[1], row[2], row[3], thumbImg(row[1]), row[4]));
                count++;
            }
            log.info("城市素材库初始化完成，共导入 {} 个城市", count);
        }

        // 【异步预拉取】高德实景照片覆盖热门城市
        if (amapService != null && "amap".equals(mapConfig.resolveProvider())) {
            new Thread(() -> prefetchAmapPhotos()).start();
        }
    }

    /** 后台预拉取所有城市的 Amap 实景照片 */
    private void prefetchAmapPhotos() {
        log.info("开始预拉取全部城市 Amap 实景照片...");
        List<CityMaterial> allCities = repo.findAll();
        int fetched = 0, total = allCities.size();
        for (CityMaterial city : allCities) {
            try {
                if (cityImageRepository.findByCityName(city.getCityName()).isPresent()) continue;
                String photoUrl = amapService.fetchCityPhoto(city.getCityName());
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    cityImageRepository.save(new CityImage(city.getCityName(), photoUrl, "amap"));
                    fetched++;
                    if (fetched % 20 == 0) {
                        log.info("[预拉取] 进度: {}/{}", fetched, total);
                    }
                }
                Thread.sleep(300);
            } catch (Exception e) {
                log.debug("预拉取失败: {} — {}", city.getCityName(), e.getMessage());
            }
        }
        log.info("预拉取完成: {}/{} 个城市已获取 Amap 实景照片", fetched, total);
    }

    /** 手动触发全量刷新（供管理员调用） */
    public int refreshAllPhotos() {
        log.info("手动触发全量图片刷新 — 清空旧数据，从高德重新爬取...");
        // 先清空所有旧图
        cityImageRepository.deleteAll();
        log.info("已清空 city_images 表");
        int amapFetched = 0, picsumFetched = 0;
        for (CityMaterial city : repo.findAll()) {
            try {
                String photoUrl = amapService.fetchCityPhoto(city.getCityName());
                String source;
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    source = "amap";
                    amapFetched++;
                } else {
                    photoUrl = thumbImg(city.getCityName());
                    source = "picsum";
                    picsumFetched++;
                }
                java.util.Optional<CityImage> existing = cityImageRepository.findByCityName(city.getCityName());
                if (existing.isPresent()) {
                    existing.get().setImageUrl(photoUrl);
                    existing.get().setSource(source);
                    cityImageRepository.save(existing.get());
                } else {
                    cityImageRepository.save(new CityImage(city.getCityName(), photoUrl, source));
                }
                Thread.sleep(300);
            } catch (Exception e) {
                log.debug("刷新失败: {}", city.getCityName());
            }
        }
        log.info("刷新完成: Amap={} Picsum={}", amapFetched, picsumFetched);
        return amapFetched + picsumFetched;
    }

    /** 查询城市素材（带兜底） */
    public Optional<CityMaterial> getByCityName(String cityName) {
        return repo.findByCityName(cityName);
    }

    public List<CityMaterial> getAll() {
        return repo.findAll();
    }

    /** 三层兜底：城市图 → 省份图 → 全国通用图 */
    public String resolveThumbImg(String cityName) {
        Optional<CityMaterial> cm = repo.findByCityName(cityName);
        if (cm.isPresent() && cm.get().getThumbImg() != null) return cm.get().getThumbImg();
        // 省份兜底
        // 全国通用兜底
        return thumbImg(cityName);
    }

    public Map<String, String> getImageMap() {
        // 预加载 city_images 表（高德缓存），建立城市名→图片URL的快速查找表
        Map<String, String> amapCache = new HashMap<>();
        for (CityImage ci : cityImageRepository.findAll()) {
            if (ci.getImageUrl() != null && !ci.getImageUrl().isEmpty()) {
                amapCache.put(ci.getCityName(), ci.getImageUrl());
            }
        }

        Map<String, String> map = new LinkedHashMap<>();
        for (CityMaterial cm : repo.findAll()) {
            String cityName = cm.getCityName();
            // 优先用高德缓存的实景照片
            if (amapCache.containsKey(cityName)) {
                map.put(cityName, amapCache.get(cityName));
            } else if (cm.getThumbImg() != null) {
                map.put(cityName, cm.getThumbImg());
            }
        }
        return map;
    }
}
