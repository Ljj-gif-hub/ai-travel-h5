-- ================================================================
-- V2: 旅行地图与行程规划初始化脚本
-- 包含 hotels、landmarks、trip_plans 三张表及种子数据
-- 适配 H2 数据库（MySQL 兼容模式）
-- ================================================================

-- ----------------------------
-- 1. 酒店表
-- ----------------------------
CREATE TABLE IF NOT EXISTS hotels (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    city            VARCHAR(50)     NOT NULL,
    district        VARCHAR(100),
    address         VARCHAR(500),
    latitude        DOUBLE,
    longitude       DOUBLE,
    price_per_night DECIMAL(10,2),
    rating          DOUBLE,
    image_url       VARCHAR(500),
    amenities       CLOB,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 2. 地标表
-- ----------------------------
CREATE TABLE IF NOT EXISTS landmarks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200)    NOT NULL,
    city        VARCHAR(50)     NOT NULL,
    type        VARCHAR(20)     NOT NULL,
    latitude    DOUBLE,
    longitude   DOUBLE,
    description CLOB,
    icon_url    VARCHAR(500),
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 3. 行程规划表
-- ----------------------------
CREATE TABLE IF NOT EXISTS trip_plans (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    destination     VARCHAR(100)    NOT NULL,
    days            INT             NOT NULL,
    people          INT             NOT NULL DEFAULT 1,
    total_budget    DECIMAL(12,2),
    hotel_cost      DECIMAL(12,2),
    ticket_cost     DECIMAL(12,2),
    food_cost       DECIMAL(12,2),
    transport_cost  DECIMAL(12,2),
    plan_json       CLOB,
    hotel_ids       VARCHAR(500),
    status          VARCHAR(20)     DEFAULT 'completed',
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================
-- 种子数据: 北京酒店（10家）
-- 使用 MERGE 语句避免重复插入 (H2 兼容语法)
-- ================================================================
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(1,  '北京王府井希尔顿酒店',           '北京', '东城区', '王府井大街8号',           39.9142, 116.4105, 1280.00, 4.8, 'https://picsum.photos/id/1/400/300',  '["免费WiFi","室内泳池","健身中心","行政酒廊","停车场"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(2,  '北京国贸大酒店',                 '北京', '朝阳区', '建国门外大街1号',         39.9087, 116.4605, 1580.00, 4.9, 'https://picsum.photos/id/2/400/300',  '["免费WiFi","无边泳池","SPA中心","商务中心","接机服务"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(3,  '北京前门建国饭店',               '北京', '西城区', '前门西大街14号',          39.9005, 116.3915,  580.00, 4.3, 'https://picsum.photos/id/3/400/300',  '["免费WiFi","中式餐厅","茶室","行李寄存"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(4,  '北京颐和安缦酒店',               '北京', '海淀区', '颐和园宫门前街1号',       39.9982, 116.2752, 3200.00, 4.9, 'https://picsum.photos/id/4/400/300',  '["免费WiFi","私人庭院","瑜伽室","图书馆","管家服务"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(5,  '北京丽晶酒店',                   '北京', '东城区', '金宝街99号',              39.9171, 116.4238,  960.00, 4.6, 'https://picsum.photos/id/5/400/300',  '["免费WiFi","室内泳池","桑拿房","会议室"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(6,  '北京中关村皇冠假日酒店',         '北京', '海淀区', '知春路106号',             39.9820, 116.3336,  720.00, 4.4, 'https://picsum.photos/id/6/400/300',  '["免费WiFi","健身中心","自助早餐","洗衣服务"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(7,  '北京三里屯通盈中心洲际酒店',     '北京', '朝阳区', '南三里屯路1号',          39.9318, 116.4551, 1380.00, 4.7, 'https://picsum.photos/id/7/400/300',  '["免费WiFi","顶层酒吧","室内泳池","SPA","代客泊车"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(8,  '北京什刹海皮影文化酒店',         '北京', '西城区', '松树街24号',             39.9390, 116.3835,  480.00, 4.2, 'https://picsum.photos/id/8/400/300',  '["免费WiFi","文化体验","四合院庭院","自行车租赁"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(9,  '北京盘古大观酒店',               '北京', '朝阳区', '北四环中路27号',          39.9882, 116.3922, 2200.00, 4.8, 'https://picsum.photos/id/9/400/300',  '["免费WiFi","全景落地窗","空中花园","私人管家","直升机停机坪"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(10, '北京徽商故里酒店',               '北京', '东城区', '南锣鼓巷16号',           39.9371, 116.4032,  350.00, 4.0, 'https://picsum.photos/id/10/400/300', '["免费WiFi","胡同庭院","徽派建筑","茶文化体验"]');

-- ================================================================
-- 种子数据: 上海酒店（8家）
-- ================================================================
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(11, '上海外滩华尔道夫酒店',           '上海', '黄浦区',   '中山东一路2号',           31.2397, 121.4903, 2600.00, 4.9, 'https://picsum.photos/id/11/400/300', '["免费WiFi","外滩江景","米其林餐厅","SPA","管家服务"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(12, '上海浦东丽思卡尔顿酒店',         '上海', '浦东新区', '世纪大道8号',             31.2354, 121.5016, 2800.00, 4.9, 'https://picsum.photos/id/12/400/300', '["免费WiFi","天际泳池","行政酒廊","豪车接送"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(13, '上海静安瑞吉酒店',               '上海', '静安区',   '北京西路1008号',          31.2288, 121.4503, 1680.00, 4.7, 'https://picsum.photos/id/13/400/300', '["免费WiFi","室内泳池","爵士酒吧","24小时管家"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(14, '上海新天地朗廷酒店',             '上海', '黄浦区',   '马当路99号',             31.2197, 121.4734, 1200.00, 4.6, 'https://picsum.photos/id/14/400/300', '["免费WiFi","英式下午茶","健身中心","商务中心"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(15, '上海豫园万丽酒店',               '上海', '黄浦区',   '河南南路159号',          31.2267, 121.4890,  780.00, 4.4, 'https://picsum.photos/id/15/400/300', '["免费WiFi","城隍庙景观","屋顶酒吧","会议室"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(16, '上海徐汇禧玥酒店',               '上海', '徐汇区',   '虹桥路1号',              31.1970, 121.4310,  620.00, 4.3, 'https://picsum.photos/id/16/400/300', '["免费WiFi","法式庭院","图书馆","自行车租赁"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(17, '上海虹桥康得思酒店',             '上海', '闵行区',   '申长路688号',            31.1969, 121.3169,  880.00, 4.5, 'https://picsum.photos/id/17/400/300', '["免费WiFi","机场班车","室内泳池","川菜餐厅"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(18, '上海五角场凯悦酒店',             '上海', '杨浦区',   '国定东路88号',           31.2988, 121.5150,  680.00, 4.3, 'https://picsum.photos/id/18/400/300', '["免费WiFi","健身中心","自助早餐","洗衣服务"]');

-- ================================================================
-- 种子数据: 巴黎酒店（6家）
-- ================================================================
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(19, '巴黎丽兹酒店 (Ritz Paris)',           '巴黎', '第一区',   '15 Place Vendôme, 75001 Paris',      48.8683, 2.3288,  8500.00, 4.9, 'https://picsum.photos/id/19/400/300', '["免费WiFi","米其林二星餐厅","香奈儿SPA","管家服务","室内泳池"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(20, '巴黎香格里拉酒店',                   '巴黎', '第十六区', '10 Avenue dIéna, 75116 Paris',        48.8650, 2.2937,  7200.00, 4.8, 'https://picsum.photos/id/20/400/300', '["免费WiFi","埃菲尔铁塔景观","法式花园","室内泳池","粤菜餐厅"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(21, '巴黎铂尔曼埃菲尔铁塔酒店',           '巴黎', '第十五区', '18 Avenue de Suffren, 75015 Paris',   48.8555, 2.2923,  3200.00, 4.5, 'https://picsum.photos/id/21/400/300', '["免费WiFi","铁塔景观房","健身中心","露台餐厅"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(22, '巴黎左岸美居酒店',                   '巴黎', '第六区',   '6 Rue de Vaugirard, 75006 Paris',     48.8498, 2.3400,  1800.00, 4.2, 'https://picsum.photos/id/22/400/300', '["免费WiFi","拉丁区中心","法式早餐","24小时前台"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(23, '巴黎蒙马特伊甸园酒店',               '巴黎', '第十八区', '90 Rue des Martyrs, 75018 Paris',     48.8867, 2.3359,  1100.00, 4.0, 'https://picsum.photos/id/23/400/300', '["免费WiFi","圣心堂景观","艺术家风格","露台花园"]');
MERGE INTO hotels (id, name, city, district, address, latitude, longitude, price_per_night, rating, image_url, amenities) KEY(id) VALUES
(24, '巴黎玛黑区精品酒店',                 '巴黎', '第四区',   '30 Rue des Archives, 75004 Paris',    48.8584, 2.3572,  2500.00, 4.4, 'https://picsum.photos/id/24/400/300', '["免费WiFi","设计师风格","庭院早餐","自行车租赁","葡萄酒吧"]');

-- ================================================================
-- 种子数据: 北京地标（15个）
-- ================================================================
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(1,  '故宫博物院',       '北京', 'attraction', 39.9163, 116.3972, '明清两代的皇家宫殿，世界文化遗产，中国最大的古代文化艺术博物馆。',             'https://picsum.photos/id/101/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(2,  '天安门广场',       '北京', 'attraction', 39.9087, 116.3975, '世界上最大的城市中心广场，国家象征，每天举行升降旗仪式。',                     'https://picsum.photos/id/102/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(3,  '颐和园',           '北京', 'attraction', 39.9999, 116.2755, '中国现存规模最大、保存最完整的皇家园林，被誉为"皇家园林博物馆"。',             'https://picsum.photos/id/103/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(4,  '八达岭长城',       '北京', 'attraction', 40.3597, 116.0200, '明长城中保存最完好且最具代表性的一段，"不到长城非好汉"的由来地。',           'https://picsum.photos/id/104/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(5,  '天坛公园',         '北京', 'attraction', 39.8822, 116.4066, '明清皇帝祭天祈谷的场所，以精妙的建筑声学设计闻名于世。',                       'https://picsum.photos/id/105/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(6,  '鸟巢(国家体育场)', '北京', 'attraction', 39.9928, 116.3955, '2008年北京奥运会主体育场，独特钢结构外观，现为城市文化地标。',                 'https://picsum.photos/id/106/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(7,  '南锣鼓巷',         '北京', 'attraction', 39.9375, 116.4037, '北京最古老的胡同街区之一，融合传统民居与时尚小店的文化休闲地。',               'https://picsum.photos/id/107/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(8,  '798艺术区',        '北京', 'attraction', 39.9842, 116.4951, '由老工业厂房改造而成的当代艺术聚集地，北京文艺青年的打卡圣地。',               'https://picsum.photos/id/108/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(9,  '天安门东站',       '北京', 'metro',      39.9140, 116.4010, '北京地铁1号线站点，前往故宫和国家博物馆的主要地铁出口。',                     'https://picsum.photos/id/201/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(10, '西单站',           '北京', 'metro',      39.9121, 116.3731, '北京地铁1号线和4号线换乘站，连接西单商业区和金融街。',                        'https://picsum.photos/id/202/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(11, '国贸站',           '北京', 'metro',      39.9087, 116.4605, '北京地铁1号线和10号线换乘站，CBD核心区域，连接国贸商圈。',                    'https://picsum.photos/id/203/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(12, '奥林匹克公园站',   '北京', 'metro',      39.9950, 116.3900, '北京地铁8号线和15号线换乘站，鸟巢、水立方等奥运场馆的主要交通枢纽。',          'https://picsum.photos/id/204/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(13, '中央电视台总部大楼','北京', 'landmark',   39.9139, 116.4648, '俗称"大裤衩"，北京CBD的地标性建筑，由荷兰建筑师库哈斯设计。',               'https://picsum.photos/id/301/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(14, '中国国家大剧院',   '北京', 'landmark',   39.9035, 116.3838, '俗称"巨蛋"，位于人民大会堂西侧，世界级演艺殿堂，独特的椭球壳体建筑。',        'https://picsum.photos/id/302/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(15, '钟鼓楼',           '北京', 'landmark',   39.9407, 116.3940, '北京中轴线北端标志性建筑，钟楼和鼓楼合称，老北京记忆的重要载体。',             'https://picsum.photos/id/303/100/100');

-- ================================================================
-- 种子数据: 上海地标（12个）
-- ================================================================
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(16, '外滩',             '上海', 'attraction', 31.2397, 121.4903, '上海最具代表性的景观带，万国建筑博览群与浦东摩天楼隔江相望。',               'https://picsum.photos/id/101/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(17, '东方明珠电视塔',   '上海', 'attraction', 31.2397, 121.4997, '浦东新区标志性建筑，高468米，设有观光层、旋转餐厅和上海历史陈列馆。',          'https://picsum.photos/id/102/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(18, '豫园',             '上海', 'attraction', 31.2272, 121.4925, '明代江南古典园林，已有400余年历史，城隍庙商圈的核心景区。',                    'https://picsum.photos/id/103/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(19, '上海迪士尼乐园',   '上海', 'attraction', 31.1443, 121.6591, '中国内地首座迪士尼主题乐园，拥有七大主题园区和迪士尼城堡。',                   'https://picsum.photos/id/104/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(20, '南京路步行街',     '上海', 'attraction', 31.2352, 121.4753, '上海最繁华的商业街，被誉为"中华第一商业街"，全长约1.2公里。',                 'https://picsum.photos/id/105/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(21, '新天地',           '上海', 'attraction', 31.2197, 121.4734, '上海时尚地标，石库门老建筑与现代商业完美融合的高端休闲区。',                   'https://picsum.photos/id/106/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(22, '人民广场站',       '上海', 'metro',      31.2324, 121.4752, '上海地铁1/2/8号线换乘站，上海城市交通的心脏枢纽。',                             'https://picsum.photos/id/201/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(23, '静安寺站',         '上海', 'metro',      31.2246, 121.4483, '上海地铁2/7号线换乘站，静安寺商圈核心，毗邻千年古刹静安寺。',                 'https://picsum.photos/id/202/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(24, '陆家嘴站',         '上海', 'metro',      31.2389, 121.5016, '上海地铁2号线站点，浦东金融核心区，东方明珠等摩天大楼的交通出口。',             'https://picsum.photos/id/203/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(25, '上海中心大厦',     '上海', 'landmark',   31.2355, 121.5016, '中国第一高楼(632米)，世界第二高楼，螺旋上升的外观象征中国经济的腾飞。',         'https://picsum.photos/id/301/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(26, '武康大楼',         '上海', 'landmark',   31.2072, 121.4390, '上海最负盛名的历史建筑之一，独特的船型外观曾是老上海的文化地标。',              'https://picsum.photos/id/302/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(27, '上海火车站',       '上海', 'metro',      31.2527, 121.4553, '上海地铁1/3/4号线换乘站，连接上海站及长途客运总站。',                          'https://picsum.photos/id/204/100/100');

-- ================================================================
-- 种子数据: 巴黎地标（10个）
-- ================================================================
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(28, '埃菲尔铁塔',       '巴黎', 'attraction', 48.8584, 2.2945, '巴黎的象征，1889年建成时为世界最高建筑，每年吸引超过700万游客。',              'https://picsum.photos/id/101/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(29, '卢浮宫',           '巴黎', 'attraction', 48.8606, 2.3376, '世界四大博物馆之首，馆藏《蒙娜丽莎》、《胜利女神》等人类艺术瑰宝。',             'https://picsum.photos/id/102/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(30, '巴黎圣母院',       '巴黎', 'attraction', 48.8530, 2.3499, '法国哥特式建筑巅峰之作，2019年火灾后正在修复中。',                             'https://picsum.photos/id/103/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(31, '凯旋门',           '巴黎', 'attraction', 48.8738, 2.2950, '拿破仑下令建造的纪念性建筑，位于香榭丽舍大街西端，12条大道交汇的星形广场。',     'https://picsum.photos/id/104/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(32, '凡尔赛宫',         '巴黎', 'attraction', 48.8049, 2.1204, '法国王权的象征，世界五大宫殿之一，镜厅和凡尔赛花园举世闻名。',                   'https://picsum.photos/id/105/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(33, '蒙马特高地',       '巴黎', 'attraction', 48.8867, 2.3431, '巴黎最具波西米亚风情的街区，圣心大教堂所在地，艺术家的灵感天堂。',               'https://picsum.photos/id/106/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(34, '夏特莱站',         '巴黎', 'metro',      48.8586, 2.3472, '巴黎地铁最大的换乘枢纽之一，连接1/4/7/11/14号线。',                             'https://picsum.photos/id/201/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(35, '歌剧院站',         '巴黎', 'metro',      48.8704, 2.3321, '巴黎地铁3/7/8号线换乘站，加尼叶歌剧院和老佛爷百货的交通枢纽。',                'https://picsum.photos/id/202/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(36, '先贤祠',           '巴黎', 'landmark',   48.8462, 2.3464, '新古典主义建筑杰作，伏尔泰、雨果、居里夫人等法国伟人的安息之地。',              'https://picsum.photos/id/301/100/100');
MERGE INTO landmarks (id, name, city, type, latitude, longitude, description, icon_url) KEY(id) VALUES
(37, '巴黎市政厅',       '巴黎', 'landmark',   48.8566, 2.3522, '法国新文艺复兴风格建筑，巴黎市政府所在地，前广场常有文化活动和冬季溜冰场。',     'https://picsum.photos/id/302/100/100');
