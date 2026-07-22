/**
 * 城市图片爬取脚本 V2 — 多源策略确保正确图片
 *
 * 策略（按优先级）：
 * 1. Wikipedia API — 免费、准确、高质量
 * 2. 百度百科 — 国内城市更准确
 * 3. Bing 图片搜索 — 多关键词 + 严格评分兜底
 *
 * 用法：node scripts/scrape-city-images-v2.cjs
 * 输出：public/city-images.json
 *
 * 支持断点续爬 + 自动检测低质量图片
 */

const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

// ==================== 所有城市列表 ====================
const CITIES = [
  '北京','上海','天津','重庆',
  '广州','深圳','珠海','东莞','佛山','中山','惠州','汕头','江门','湛江','茂名','肇庆','梅州','汕尾','河源','阳江','清远','潮州','揭阳','云浮','韶关',
  '杭州','宁波','温州','嘉兴','湖州','绍兴','金华','舟山','衢州','台州','丽水',
  '南京','苏州','无锡','常州','南通','扬州','镇江','徐州','盐城','泰州','淮安','连云港','宿迁',
  '成都','绵阳','德阳','宜宾','南充','泸州','乐山','眉山','自贡','攀枝花','广元','遂宁','内江','广安','达州','雅安','巴中','资阳','阿坝','甘孜','凉山',
  '武汉','宜昌','襄阳','荆州','黄石','十堰','鄂州','孝感','黄冈','咸宁','随州','恩施','荆门','仙桃','天门','潜江','神农架',
  '长沙','株洲','湘潭','衡阳','岳阳','常德','张家界','郴州','益阳','永州','怀化','娄底','邵阳','吉首','凤凰','湘西',
  '福州','厦门','泉州','漳州','莆田','龙岩','三明','南平','宁德','武夷山','平潭',
  '济南','青岛','烟台','威海','潍坊','淄博','临沂','济宁','泰安','日照','德州','聊城','滨州','菏泽','枣庄','东营','曲阜',
  '郑州','洛阳','开封','南阳','许昌','新乡','安阳','信阳','商丘','周口','驻马店','平顶山','焦作','濮阳','漯河','三门峡','鹤壁','济源',
  '石家庄','唐山','保定','邯郸','廊坊','沧州','秦皇岛','张家口','承德','邢台','衡水','雄安新区',
  '沈阳','大连','鞍山','抚顺','本溪','丹东','锦州','营口','阜新','辽阳','盘锦','铁岭','朝阳','葫芦岛',
  '西安','咸阳','宝鸡','渭南','延安','汉中','榆林','安康','商洛','铜川','华山',
  '昆明','大理','丽江','香格里拉','西双版纳','腾冲','普洱','曲靖','玉溪','保山','昭通','临沧','楚雄','红河','文山','德宏','怒江','迪庆',
  '贵阳','遵义','安顺','毕节','铜仁','六盘水','凯里','镇远','荔波','黄果树','梵净山','黔东南','黔南','黔西南',
  '南宁','桂林','柳州','北海','玉林','梧州','防城港','钦州','贵港','百色','贺州','河池','来宾','崇左','阳朔','涠洲岛',
  '海口','三亚','儋州','琼海','文昌','万宁','五指山','东方','陵水','乐东','澄迈','临高','定安','屯昌','昌江','保亭','琼中','三沙',
  '合肥','芜湖','蚌埠','淮南','马鞍山','安庆','黄山','阜阳','宿州','滁州','六安','宣城','池州','亳州','铜陵','淮北','九华山',
  '南昌','九江','景德镇','赣州','上饶','宜春','吉安','抚州','萍乡','新余','鹰潭','庐山','婺源','井冈山','三清山',
  '太原','大同','阳泉','长治','晋城','临汾','运城','吕梁','晋中','忻州','朔州','平遥','五台山','壶口瀑布',
  '长春','吉林','四平','辽源','通化','白山','延边','松原','白城','长白山',
  '哈尔滨','齐齐哈尔','牡丹江','佳木斯','大庆','鸡西','鹤岗','黑河','双鸭山','伊春','七台河','绥化','大兴安岭','漠河','雪乡','亚布力',
  '兰州','嘉峪关','天水','武威','张掖','酒泉','敦煌','平凉','金昌','白银','定西','陇南','庆阳','临夏','甘南',
  '呼和浩特','包头','鄂尔多斯','呼伦贝尔','赤峰','通辽','乌海','巴彦淖尔','乌兰察布','兴安盟','锡林郭勒','阿拉善','满洲里','阿尔山','额济纳',
  '乌鲁木齐','克拉玛依','吐鲁番','哈密','喀什','伊犁','阿勒泰','库尔勒','阿克苏','和田','昌吉','博尔塔拉','巴音郭楞','克孜勒苏','塔城','喀纳斯','那拉提','赛里木湖',
  '拉萨','日喀则','林芝','山南','那曲','昌都','阿里','珠峰大本营','纳木错','羊卓雍措','雅鲁藏布大峡谷',
  '西宁','海东','格尔木','德令哈','玉树','果洛','海北','黄南','海南','海西','青海湖','茶卡盐湖','祁连',
  '银川','石嘴山','吴忠','固原','中卫','沙坡头','贺兰山',
  '台北','新北','高雄','台中','台南','桃园','花莲','台东','宜兰','南投','嘉义','屏东','苗栗','新竹','彰化','云林','澎湖','金门','马祖','基隆',
  '日月潭','清境农场','合欢山','阿里山','垦丁','太鲁阁','九份','淡水','十分','阳明山','野柳','逢甲','高美湿地',
  '东京','大阪','京都','札幌','福冈','名古屋','神户','奈良','冲绳','横滨','箱根','镰仓','富士山','小樽','函馆',
  '首尔','釜山','济州','仁川','大邱','光州','大田','蔚山','江原道','庆州','全州','水原',
  '曼谷','清迈','普吉岛','芭提雅','苏梅岛','甲米','华欣','大城','清莱','拜县','斯米兰','皮皮岛',
  '新加坡','圣淘沙','乌节路','牛车水','小印度','克拉码头','滨海湾',
  '吉隆坡','槟城','马六甲','兰卡威','沙巴','新山','仙本那','热浪岛',
  '河内','胡志明市','岘港','芽庄','会安','大叻','富国岛','下龙湾','美奈','顺化',
  '巴厘岛','雅加达','龙目岛','科莫多岛','民丹岛',
  '马尼拉','长滩岛','宿务','薄荷岛','巴拉望','锡亚高','爱妮岛',
  '迪拜','阿布扎比',
  '新德里','孟买','阿格拉','斋浦尔','班加罗尔','瓦拉纳西',
  '金边','暹粒','吴哥窟',
  '马尔代夫','马累',
  '科伦坡','康提','加勒',
  '曼德勒','蒲甘',
  '琅勃拉邦','万象',
  '加德满都','博卡拉',
  '伊斯坦布尔','卡帕多奇亚','安塔利亚','棉花堡',
  '耶路撒冷','特拉维夫',
  '佩特拉','死海','安曼',
  '巴黎','尼斯','里昂','马赛','波尔多','戛纳','阿维尼翁','科尔马','普罗旺斯','霞慕尼',
  '罗马','威尼斯','米兰','佛罗伦萨','比萨','五渔村','阿马尔菲','科莫湖','多洛米蒂','西西里',
  '伦敦','爱丁堡','曼彻斯特','利物浦','牛津','剑桥','巴斯','约克','格拉斯哥','天空岛','巨石阵','科茨沃尔德',
  '柏林','慕尼黑','法兰克福','汉堡','科隆','海德堡','斯图加特','纽伦堡','德累斯顿','新天鹅堡','国王湖','黑森林',
  '巴塞罗那','马德里','塞维利亚','格拉纳达','瓦伦西亚','马拉加','毕尔巴鄂','托莱多','龙达','马略卡岛','伊维萨岛',
  '苏黎世','日内瓦','卢塞恩','因特拉肯','伯尔尼','洛桑','采尔马特','少女峰','蒙特勒','格林德瓦',
  '雅典','圣托里尼','米克诺斯','克里特','罗德岛','扎金索斯','梅黛奥拉','德尔斐',
  '阿姆斯特丹','鹿特丹','海牙','代尔夫特','羊角村','风车村','库肯霍夫',
  '布拉格','克鲁姆洛夫','维也纳','萨尔茨堡','哈尔施塔特','因斯布鲁克',
  '雷克雅未克','蓝湖','冰河湖','黄金圈',
  '奥斯陆','卑尔根','特罗姆瑟','罗弗敦群岛','松恩峡湾',
  '斯德哥尔摩','哥德堡','基律纳','赫尔辛基','罗瓦涅米','圣诞老人村','拉普兰',
  '哥本哈根','法罗群岛',
  '里斯本','波尔图','辛特拉','法鲁','马德拉群岛',
  '布达佩斯','巴拉顿湖','杜布罗夫尼克','十六湖','华沙','克拉科夫',
  '都柏林','莫赫悬崖','布鲁塞尔','布鲁日',
  '莫斯科','圣彼得堡','喀山','索契','贝加尔湖','摩尔曼斯克',
  '纽约','洛杉矶','旧金山','拉斯维加斯','芝加哥','华盛顿','波士顿','西雅图','迈阿密','奥兰多','圣地亚哥','费城','夏威夷','黄石公园','大峡谷','优胜美地','阿拉斯加',
  '多伦多','温哥华','蒙特利尔','渥太华','卡尔加里','魁北克','班夫','维多利亚','贾斯珀','惠斯勒','尼亚加拉瀑布',
  '墨西哥城','坎昆','瓜达拉哈拉','哈瓦那','巴拉德罗',
  '里约热内卢','圣保罗','伊瓜苏','布宜诺斯艾利斯','乌斯怀亚','埃尔卡拉法特',
  '库斯科','马丘比丘','乌尤尼盐沼','加拉帕戈斯群岛','复活节岛','百内国家公园',
  '悉尼','墨尔本','黄金海岸','布里斯班','珀斯','凯恩斯','阿德莱德','霍巴特','达尔文','乌鲁鲁','大堡礁','大洋路','袋鼠岛',
  '奥克兰','皇后镇','基督城','惠灵顿','罗托鲁瓦','米尔福德峡湾','库克山','霍比屯','蒂卡波湖','福克斯冰川',
  '楠迪','波拉波拉岛','水母湖',
  '南极半岛','德雷克海峡',
  '开罗','卢克索','阿斯旺','阿布辛贝','锡瓦绿洲',
  '马拉喀什','卡萨布兰卡','菲斯','舍夫沙万','撒哈拉','丹吉尔',
  '开普敦','约翰内斯堡','花园大道','克鲁格国家公园',
  '内罗毕','马赛马拉','桑给巴尔','塞伦盖蒂','乞力马扎罗',
  '路易港','蓝湾','突尼斯城','蓝白小镇','温得和克',
]
const UNIQUE_CITIES = [...new Set(CITIES)]

// ==================== 配置 ====================
const OUTPUT = path.join(__dirname, '..', 'public', 'city-images.json')
const DELAY_MS = 800
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'

// 明显不是旅游风景照的来源/关键词
const BLOCKED_PATTERNS = [
  'icon', 'logo', 'avatar', 'chart', 'graph', 'barcode', 'qr', 'thumb',
  'product', 'mall', 'shop', 'price', 'taobao', 'jd.com', 'alibaba',
  'mapamundi', 'politico', 'nombres', 'breed', 'identifier', 'font.',
  'code/', 'code.', '/code', 'desktop-wallpaper', 'wallpaper-4k',
  'discount', 'coupon', 'promo', 'banner', 'ad.', '/ad/', 'advert',
  'screenshot', 'diagram', 'flowchart', 'infographic',
  'portrait', 'selfie', 'headshot', 'passport',
  'document', 'form', 'contract', 'invoice',
  'food', 'recipe', 'cooking', 'dish', 'menu-',
  'game', 'gaming', 'meme', 'funny',
  'cat-', 'dog-', 'pet-', 'animal',
  'piano', 'music-', 'sheet-music',
  'chemistry', 'covalent', 'molecule',
  'equestrian', 'horse-saddle',
  'car-', 'auto-part', 'tire-',
  'pharmacy', 'drug-', 'medicine',
  'eiffel-tower', 'paris-', // 巴黎铁塔经常被误匹配到其他城市
]

// 优质域名
const GOOD_DOMAINS = [
  'wikipedia', 'wikimedia', 'commons.wikimedia',
  'ctrip.com', 'qunar.com', 'tuniu.com', 'mafengwo',
  '699pic.com', 'nipic.com', 'tuchong.com',
  'pixabay.com', 'pexels.com', 'unsplash.com', 'flickr.com',
  'zhimg.com', 'bcebos.com', 'baidu.com',
  'gettyimages', 'shutterstock', 'istockphoto', 'alamy',
]

// ==================== 已知正确的 Wikipedia 页面映射 ====================
// 中文城市在 Wikipedia 上的英文页面名
const WIKI_OVERRIDES = {
  '北京': 'Beijing', '上海': 'Shanghai', '天津': 'Tianjin', '重庆': 'Chongqing',
  '广州': 'Guangzhou', '深圳': 'Shenzhen', '珠海': 'Zhuhai', '东莞': 'Dongguan',
  '佛山': 'Foshan', '中山': 'Zhongshan', '惠州': 'Huizhou', '汕头': 'Shantou',
  '江门': 'Jiangmen', '湛江': 'Zhanjiang', '茂名': 'Maoming', '肇庆': 'Zhaoqing',
  '梅州': 'Meizhou', '汕尾': 'Shanwei', '河源': 'Heyuan', '阳江': 'Yangjiang',
  '清远': 'Qingyuan', '潮州': 'Chaozhou', '揭阳': 'Jieyang', '云浮': 'Yunfu', '韶关': 'Shaoguan',
  '杭州': 'Hangzhou', '宁波': 'Ningbo', '温州': 'Wenzhou', '嘉兴': 'Jiaxing',
  '湖州': 'Huzhou', '绍兴': 'Shaoxing', '金华': 'Jinhua', '舟山': 'Zhoushan',
  '衢州': 'Quzhou', '台州': 'Taizhou,_Zhejiang', '丽水': 'Lishui,_Zhejiang',
  '南京': 'Nanjing', '苏州': 'Suzhou', '无锡': 'Wuxi', '常州': 'Changzhou',
  '南通': 'Nantong', '扬州': 'Yangzhou', '镇江': 'Zhenjiang', '徐州': 'Xuzhou',
  '盐城': 'Yancheng', '泰州': 'Taizhou,_Jiangsu', '淮安': "Huai'an", '连云港': 'Lianyungang', '宿迁': 'Suqian',
  '成都': 'Chengdu', '绵阳': 'Mianyang', '德阳': 'Deyang', '宜宾': 'Yibin',
  '南充': 'Nanchong', '泸州': 'Luzhou', '乐山': 'Leshan', '眉山': 'Meishan',
  '自贡': 'Zigong', '攀枝花': 'Panzhihua', '广元': 'Guangyuan', '遂宁': 'Suining',
  '内江': 'Neijiang', '广安': "Guang'an", '达州': 'Dazhou,_Sichuan', '雅安': "Ya'an",
  '巴中': 'Bazhong', '资阳': 'Ziyang,_Sichuan', '阿坝': 'Ngawa_Tibetan_and_Qiang_Autonomous_Prefecture',
  '甘孜': 'Garzê_Tibetan_Autonomous_Prefecture', '凉山': 'Liangshan_Yi_Autonomous_Prefecture',
  '武汉': 'Wuhan', '宜昌': 'Yichang', '襄阳': 'Xiangyang', '荆州': 'Jingzhou',
  '黄石': 'Huangshi', '十堰': 'Shiyan', '鄂州': 'Ezhou', '孝感': 'Xiaogan',
  '黄冈': 'Huanggang,_Hubei', '咸宁': 'Xianning', '随州': 'Suizhou', '恩施': 'Enshi_City',
  '荆门': 'Jingmen', '仙桃': 'Xiantao', '天门': 'Tianmen', '潜江': 'Qianjiang,_Hubei', '神农架': 'Shennongjia',
  '长沙': 'Changsha', '株洲': 'Zhuzhou', '湘潭': 'Xiangtan', '衡阳': 'Hengyang',
  '岳阳': 'Yueyang', '常德': 'Changde', '张家界': 'Zhangjiajie', '郴州': 'Chenzhou',
  '益阳': 'Yiyang', '永州': 'Yongzhou', '怀化': 'Huaihua', '娄底': 'Loudi',
  '邵阳': 'Shaoyang', '湘西': 'Xiangxi_Tujia_and_Miao_Autonomous_Prefecture',
  '福州': 'Fuzhou', '厦门': 'Xiamen', '泉州': 'Quanzhou', '漳州': 'Zhangzhou',
  '莆田': 'Putian', '龙岩': 'Longyan', '三明': 'Sanming', '南平': 'Nanping', '宁德': 'Ningde',
  '济南': 'Jinan', '青岛': 'Qingdao', '烟台': 'Yantai', '威海': 'Weihai',
  '潍坊': 'Weifang', '淄博': 'Zibo', '临沂': 'Linyi', '济宁': 'Jining',
  '泰安': "Tai'an", '日照': 'Rizhao', '德州': 'Dezhou', '聊城': 'Liaocheng',
  '滨州': 'Binzhou,_Shandong', '菏泽': 'Heze', '枣庄': 'Zaozhuang', '东营': 'Dongying',
  '郑州': 'Zhengzhou', '洛阳': 'Luoyang', '开封': 'Kaifeng', '南阳': 'Nanyang,_Henan',
  '许昌': 'Xuchang', '新乡': 'Xinxiang', '安阳': 'Anyang', '信阳': 'Xinyang',
  '商丘': 'Shangqiu', '周口': 'Zhoukou', '驻马店': 'Zhumadian', '平顶山': 'Pingdingshan',
  '焦作': 'Jiaozuo', '濮阳': 'Puyang,_Henan', '漯河': 'Luohe', '三门峡': 'Sanmenxia', '鹤壁': 'Hebi',
  '石家庄': 'Shijiazhuang', '唐山': 'Tangshan', '保定': 'Baoding', '邯郸': 'Handan',
  '廊坊': 'Langfang', '沧州': 'Cangzhou', '秦皇岛': 'Qinhuangdao', '张家口': 'Zhangjiakou',
  '承德': 'Chengde', '邢台': 'Xingtai', '衡水': 'Hengshui',
  '沈阳': 'Shenyang', '大连': 'Dalian', '鞍山': 'Anshan,_Liaoning',
  '抚顺': 'Fushun', '本溪': 'Benxi', '丹东': 'Dandong', '锦州': 'Jinzhou',
  '营口': 'Yingkou', '阜新': 'Fuxin', '辽阳': 'Liaoyang', '盘锦': 'Panjin',
  '铁岭': 'Tieling', '葫芦岛': 'Huludao',
  '西安': "Xi'an", '咸阳': 'Xianyang', '宝鸡': 'Baoji', '渭南': 'Weinan',
  '延安': "Yan'an", '汉中': 'Hanzhong', '榆林': 'Yulin,_Shaanxi', '安康': 'Ankang',
  '商洛': 'Shangluo', '铜川': 'Tongchuan',
  '昆明': 'Kunming', '大理': 'Dali_City', '丽江': 'Lijiang',
  '香格里拉': 'Shangri-La,_Yunnan', '西双版纳': 'Xishuangbanna_Dai_Autonomous_Prefecture',
  '普洱': "Pu'er_City", '曲靖': 'Qujing', '玉溪': 'Yuxi', '保山': 'Baoshan,_Yunnan',
  '昭通': 'Zhaotong', '临沧': 'Lincang', '楚雄': 'Chuxiong_City', '红河': 'Honghe_Hani_and_Yi_Autonomous_Prefecture',
  '文山': 'Wenshan_City', '德宏': 'Dehong_Dai_and_Jingpo_Autonomous_Prefecture',
  '怒江': 'Nujiang_Lisu_Autonomous_Prefecture', '迪庆': 'Dêqên_Tibetan_Autonomous_Prefecture',
  '贵阳': 'Guiyang', '遵义': 'Zunyi', '安顺': 'Anshun', '毕节': 'Bijie',
  '铜仁': 'Tongren', '六盘水': 'Liupanshui', '黔东南': 'Qiandongnan_Miao_and_Dong_Autonomous_Prefecture',
  '黔南': 'Qiannan_Buyei_and_Miao_Autonomous_Prefecture', '黔西南': 'Qianxinan_Buyei_and_Miao_Autonomous_Prefecture',
  '南宁': 'Nanning', '桂林': 'Guilin', '柳州': 'Liuzhou', '北海': 'Beihai',
  '玉林': 'Yulin,_Guangxi', '梧州': 'Wuzhou', '防城港': 'Fangchenggang', '钦州': 'Qinzhou',
  '贵港': 'Guigang', '百色': 'Baise', '贺州': 'Hezhou', '河池': 'Hechi', '崇左': 'Chongzuo',
  '海口': 'Haikou', '三亚': 'Sanya', '儋州': 'Danzhou', '琼海': 'Qionghai',
  '文昌': 'Wenchang', '万宁': 'Wanning,_Hainan',
  '合肥': 'Hefei', '芜湖': 'Wuhu', '蚌埠': 'Bengbu', '淮南': 'Huainan',
  '马鞍山': "Ma'anshan", '安庆': 'Anqing', '黄山': 'Huangshan_City', '阜阳': 'Fuyang',
  '宿州': 'Suzhou,_Anhui', '滁州': 'Chuzhou', '六安': "Lu'an", '宣城': 'Xuancheng',
  '池州': 'Chizhou', '亳州': 'Bozhou', '铜陵': 'Tongling', '淮北': 'Huaibei',
  '南昌': 'Nanchang', '九江': 'Jiujiang', '景德镇': 'Jingdezhen', '赣州': 'Ganzhou',
  '上饶': 'Shangrao', '宜春': 'Yichun,_Jiangxi', '吉安': "Ji'an", '抚州': 'Fuzhou,_Jiangxi',
  '萍乡': 'Pingxiang', '新余': 'Xinyu', '鹰潭': 'Yingtan',
  '太原': 'Taiyuan', '大同': 'Datong', '阳泉': 'Yangquan', '长治': 'Changzhi',
  '晋城': 'Jincheng', '临汾': 'Linfen', '运城': 'Yuncheng',
  '吕梁': 'Lüliang', '晋中': 'Jinzhong', '忻州': 'Xinzhou', '朔州': 'Shuozhou',
  '长春': 'Changchun', '吉林': 'Jilin_City', '四平': 'Siping,_Jilin',
  '辽源': 'Liaoyuan', '通化': 'Tonghua', '白山': 'Baishan', '延边': 'Yanbian_Korean_Autonomous_Prefecture',
  '松原': 'Songyuan', '白城': 'Baicheng',
  '哈尔滨': 'Harbin', '齐齐哈尔': 'Qiqihar', '牡丹江': 'Mudanjiang', '佳木斯': 'Jiamusi',
  '大庆': 'Daqing', '鸡西': 'Jixi', '鹤岗': 'Hegang', '黑河': 'Heihe',
  '双鸭山': 'Shuangyashan', '伊春': 'Yichun,_Heilongjiang', '七台河': 'Qitaihe',
  '绥化': 'Suihua', '大兴安岭': 'Greater_Khingan',
  '兰州': 'Lanzhou', '嘉峪关': 'Jiayuguan_City', '天水': 'Tianshui',
  '武威': 'Wuwei,_Gansu', '张掖': 'Zhangye', '酒泉': 'Jiuquan', '敦煌': 'Dunhuang',
  '平凉': 'Pingliang', '金昌': 'Jinchang', '白银': 'Baiyin', '定西': 'Dingxi',
  '陇南': 'Longnan', '庆阳': 'Qingyang,_Gansu', '临夏': 'Linxia_City', '甘南': 'Gannan_Tibetan_Autonomous_Prefecture',
  '呼和浩特': 'Hohhot', '包头': 'Baotou', '鄂尔多斯': 'Ordos_City',
  '呼伦贝尔': 'Hulunbuir', '赤峰': 'Chifeng', '通辽': 'Tongliao',
  '乌鲁木齐': 'Ürümqi', '克拉玛依': 'Karamay', '吐鲁番': 'Turpan',
  '哈密': 'Hami', '喀什': 'Kashgar', '伊犁': 'Ili_Kazakh_Autonomous_Prefecture',
  '阿勒泰': 'Altay_City', '库尔勒': 'Korla', '阿克苏': 'Aksu_City', '和田': 'Hotan',
  '拉萨': 'Lhasa', '日喀则': 'Shigatse', '林芝': 'Nyingchi', '山南': 'Shannan,_Tibet',
  '那曲': 'Nagqu', '昌都': 'Chamdo',
  '西宁': 'Xining', '海东': 'Haidong', '格尔木': 'Golmud', '德令哈': 'Delingha',
  '银川': 'Yinchuan', '石嘴山': 'Shizuishan', '吴忠': 'Wuzhong,_Ningxia',
  '固原': 'Guyuan', '中卫': 'Zhongwei',
  '香港': 'Hong_Kong', '澳门': 'Macau',
  '台北': 'Taipei', '新北': 'New_Taipei_City', '高雄': 'Kaohsiung', '台中': 'Taichung',
  '台南': 'Tainan', '桃园': 'Taoyuan,_Taiwan', '花莲': 'Hualien_City', '台东': 'Taitung_City',
  '宜兰': 'Yilan_City', '南投': 'Nantou_City', '嘉义': 'Chiayi', '屏东': 'Pingtung_City',
  '苗栗': 'Miaoli_City', '新竹': 'Hsinchu', '彰化': 'Changhua_City', '云林': 'Douliu',
  '澎湖': 'Penghu', '金门': 'Kinmen', '马祖': 'Matsu_Islands', '基隆': 'Keelung',
  // 国际城市
  '东京': 'Tokyo', '大阪': 'Osaka', '京都': 'Kyoto', '札幌': 'Sapporo',
  '福冈': 'Fukuoka', '名古屋': 'Nagoya', '神户': 'Kobe', '奈良': 'Nara,_Nara',
  '冲绳': 'Okinawa_Island', '横滨': 'Yokohama', '箱根': 'Hakone', '镰仓': 'Kamakura',
  '富士山': 'Mount_Fuji', '小樽': 'Otaru', '函馆': 'Hakodate',
  '首尔': 'Seoul', '釜山': 'Busan', '济州': 'Jeju_Island', '仁川': 'Incheon',
  '曼谷': 'Bangkok', '清迈': 'Chiang_Mai', '普吉岛': 'Phuket',
  '芭提雅': 'Pattaya', '苏梅岛': 'Ko_Samui',
  '新加坡': 'Singapore', '迪拜': 'Dubai', '阿布扎比': 'Abu_Dhabi',
  '马尔代夫': 'Maldives',
  '巴厘岛': 'Bali', '雅加达': 'Jakarta',
  '巴黎': 'Paris', '尼斯': 'Nice', '里昂': 'Lyon', '马赛': 'Marseille',
  '波尔多': 'Bordeaux', '戛纳': 'Cannes',
  '罗马': 'Rome', '威尼斯': 'Venice', '米兰': 'Milan', '佛罗伦萨': 'Florence',
  '比萨': 'Pisa', '西西里': 'Sicily',
  '伦敦': 'London', '爱丁堡': 'Edinburgh', '曼彻斯特': 'Manchester',
  '利物浦': 'Liverpool', '牛津': 'Oxford', '剑桥': 'Cambridge',
  '柏林': 'Berlin', '慕尼黑': 'Munich', '法兰克福': 'Frankfurt',
  '汉堡': 'Hamburg', '科隆': 'Cologne',
  '巴塞罗那': 'Barcelona', '马德里': 'Madrid',
  '苏黎世': 'Zürich', '日内瓦': 'Geneva',
  '雅典': 'Athens', '圣托里尼': 'Santorini',
  '阿姆斯特丹': 'Amsterdam', '鹿特丹': 'Rotterdam',
  '布拉格': 'Prague', '维也纳': 'Vienna',
  '雷克雅未克': 'Reykjavík',
  '奥斯陆': 'Oslo', '卑尔根': 'Bergen',
  '斯德哥尔摩': 'Stockholm', '哥本哈根': 'Copenhagen',
  '赫尔辛基': 'Helsinki',
  '里斯本': 'Lisbon', '波尔图': 'Porto',
  '布达佩斯': 'Budapest', '华沙': 'Warsaw',
  '都柏林': 'Dublin', '布鲁塞尔': 'Brussels',
  '莫斯科': 'Moscow', '圣彼得堡': 'Saint_Petersburg',
  '纽约': 'New_York_City', '洛杉矶': 'Los_Angeles', '旧金山': 'San_Francisco',
  '拉斯维加斯': 'Las_Vegas', '芝加哥': 'Chicago', '华盛顿': 'Washington,_D.C.',
  '波士顿': 'Boston', '西雅图': 'Seattle', '迈阿密': 'Miami',
  '奥兰多': 'Orlando,_Florida', '费城': 'Philadelphia', '夏威夷': 'Hawaii',
  '黄石公园': 'Yellowstone_National_Park', '大峡谷': 'Grand_Canyon',
  '优胜美地': 'Yosemite_National_Park', '阿拉斯加': 'Alaska',
  '多伦多': 'Toronto', '温哥华': 'Vancouver', '蒙特利尔': 'Montreal',
  '渥太华': 'Ottawa', '魁北克': 'Quebec_City', '班夫': 'Banff_National_Park',
  '尼亚加拉瀑布': 'Niagara_Falls',
  '墨西哥城': 'Mexico_City', '坎昆': 'Cancún', '哈瓦那': 'Havana',
  '里约热内卢': 'Rio_de_Janeiro', '圣保罗': 'São_Paulo',
  '布宜诺斯艾利斯': 'Buenos_Aires', '伊瓜苏': 'Iguazu_Falls',
  '库斯科': 'Cusco', '马丘比丘': 'Machu_Picchu',
  '加拉帕戈斯群岛': 'Galápagos_Islands', '复活节岛': 'Easter_Island',
  '悉尼': 'Sydney', '墨尔本': 'Melbourne', '黄金海岸': 'Gold_Coast,_Queensland',
  '布里斯班': 'Brisbane', '珀斯': 'Perth', '凯恩斯': 'Cairns',
  '阿德莱德': 'Adelaide', '大堡礁': 'Great_Barrier_Reef',
  '奥克兰': 'Auckland', '皇后镇': 'Queenstown,_New_Zealand',
  '基督城': 'Christchurch', '惠灵顿': 'Wellington',
  '罗托鲁瓦': 'Rotorua', '米尔福德峡湾': 'Milford_Sound',
  '开罗': 'Cairo', '卢克索': 'Luxor',
  '马拉喀什': 'Marrakesh', '卡萨布兰卡': 'Casablanca',
  '舍夫沙万': 'Chefchaouen', '撒哈拉': 'Sahara',
  '开普敦': 'Cape_Town', '约翰内斯堡': 'Johannesburg',
  '内罗毕': 'Nairobi', '马赛马拉': 'Maasai_Mara',
  '桑给巴尔': 'Zanzibar', '塞伦盖蒂': 'Serengeti',
  '乞力马扎罗': 'Mount_Kilimanjaro',
  '伊斯坦布尔': 'Istanbul', '卡帕多奇亚': 'Cappadocia',
  '加德满都': 'Kathmandu', '博卡拉': 'Pokhara',
  '暹粒': 'Siem_Reap', '吴哥窟': 'Angkor_Wat',
  '琅勃拉邦': 'Luang_Prabang', '万象': 'Vientiane',
  '科伦坡': 'Colombo', '康提': 'Kandy',
  '耶路撒冷': 'Jerusalem', '佩特拉': 'Petra', '死海': 'Dead_Sea',
  '马尼拉': 'Manila', '长滩岛': 'Boracay',
  '河内': 'Hanoi', '胡志明市': 'Ho_Chi_Minh_City',
  '岘港': 'Da_Nang', '芽庄': 'Nha_Trang', '下龙湾': 'Ha_Long_Bay',
  '吉隆坡': 'Kuala_Lumpur', '槟城': 'Penang', '马六甲': 'Malacca',
  '新德里': 'New_Delhi', '孟买': 'Mumbai', '阿格拉': 'Agra',
  '斋浦尔': 'Jaipur', '瓦拉纳西': 'Varanasi',
  '金边': 'Phnom_Penh',
  '莫斯科': 'Moscow', '贝加尔湖': 'Lake_Baikal',
  '清境农场': 'Qingjing_Farm',
  '日月潭': 'Sun_Moon_Lake', '阿里山': 'Alishan_National_Scenic_Area',
  '垦丁': 'Kenting_National_Park', '太鲁阁': 'Taroko_National_Park',
  '九份': 'Jiufen', '淡水': 'Tamsui', '阳明山': 'Yangmingshan',
  '圣淘沙': 'Sentosa', '滨海湾': 'Marina_Bay,_Singapore',
  '吉隆坡': 'Kuala_Lumpur', '仙本那': 'Semporna',
  '新天鹅堡': 'Neuschwanstein_Castle',
  '五渔村': 'Cinque_Terre', '科莫湖': 'Lake_Como',
  '多洛米蒂': 'Dolomites', '阿马尔菲': 'Amalfi_Coast',
  '少女峰': 'Jungfrau', '马特洪峰': 'Matterhorn',
  '国王湖': 'Königssee', '黑森林': 'Black_Forest',
  '普罗旺斯': 'Provence', '霞慕尼': 'Chamonix',
  '天空岛': 'Isle_of_Skye', '巨石阵': 'Stonehenge', '科茨沃尔德': 'Cotswolds',
  '哈尔施塔特': 'Hallstatt',
  '羊角村': 'Giethoorn', '风车村': 'Zaanse_Schans', '库肯霍夫': 'Keukenhof',
  '克鲁姆洛夫': 'Český_Krumlov',
  '蓝湖': 'Blue_Lagoon_(geothermal_spa)', '冰河湖': 'Jökulsárlón',
  '罗弗敦群岛': 'Lofoten', '松恩峡湾': 'Sognefjord',
  '圣诞老人村': 'Santa_Claus_Village', '拉普兰': 'Lapland_(Finland)',
  '法罗群岛': 'Faroe_Islands',
  '莫赫悬崖': 'Cliffs_of_Moher',
  '大堡礁': 'Great_Barrier_Reef', '大洋路': 'Great_Ocean_Road', '乌鲁鲁': 'Uluru',
  '米尔福德峡湾': 'Milford_Sound', '库克山': 'Aoraki_/_Mount_Cook',
  '霍比屯': 'Hobbiton_Movie_Set',
  '波拉波拉岛': 'Bora_Bora',
  '南极半岛': 'Antarctic_Peninsula',
  '阿布辛贝': 'Abu_Simbel',
  '舍夫沙万': 'Chefchaouen',
  '克鲁格国家公园': 'Kruger_National_Park',
  '花园大道': 'Garden_Route',
  '蓝白小镇': 'Sidi_Bou_Said',
  '百内国家公园': 'Torres_del_Paine_National_Park',
  '乌尤尼盐沼': 'Salar_de_Uyuni',
  '乌斯怀亚': 'Ushuaia',
  '巴拉德罗': 'Varadero',
  '十六湖': 'Plitvice_Lakes_National_Park',
  '杜布罗夫尼克': 'Dubrovnik',
  '棉花堡': 'Pamukkale',
  '安塔利亚': 'Antalya',
  '索契': 'Sochi',
  '喀山': 'Kazan',
  '摩尔曼斯克': 'Murmansk',
  '德尔斐': 'Delphi',
  '梅黛奥拉': 'Meteora',
  '扎金索斯': 'Zakynthos',
  '米克诺斯': 'Mykonos',
  '罗德岛': 'Rhodes_(city)',
  '克里特': 'Crete',
  '格林德瓦': 'Grindelwald',
  '蒙特勒': 'Montreux',
  '采尔马特': 'Zermatt',
  '洛桑': 'Lausanne',
  '伯尔尼': 'Bern',
  '因特拉肯': 'Interlaken',
  '卢塞恩': 'Lucerne',
  '因斯布鲁克': 'Innsbruck',
  '萨尔茨堡': 'Salzburg',
  '龙达': 'Ronda',
  '托莱多': 'Toledo,_Spain',
  '毕尔巴鄂': 'Bilbao',
  '格拉纳达': 'Granada',
  '塞维利亚': 'Seville',
  '瓦伦西亚': 'Valencia',
  '德累斯顿': 'Dresden',
  '纽伦堡': 'Nuremberg',
  '斯图加特': 'Stuttgart',
  '海德堡': 'Heidelberg',
  '巴斯': 'Bath,_Somerset',
  '约克': 'York',
  '格拉斯哥': 'Glasgow',
  '科尔马': 'Colmar',
  '阿维尼翁': 'Avignon',
  '阿马尔菲': 'Amalfi_Coast',
  '多洛米蒂': 'Dolomites',
  '比萨': 'Pisa',
  '那不勒斯': 'Naples',
  '博洛尼亚': 'Bologna',
  '维罗纳': 'Verona',
  '马略卡岛': 'Mallorca',
  '伊维萨岛': 'Ibiza',
  '马拉加': 'Málaga',
  '华欣': 'Hua_Hin',
  '清莱': 'Chiang_Rai',
  '大城': 'Ayutthaya_(city)',
  '甲米': 'Krabi',
  '拜县': 'Pai,_Thailand',
  '斯米兰': 'Similan_Islands',
  '皮皮岛': 'Phi_Phi_Islands',
  '圣淘沙': 'Sentosa',
  '牛车水': 'Chinatown,_Singapore',
  '小印度': 'Little_India,_Singapore',
  '克拉码头': 'Clarke_Quay',
  '兰卡威': 'Langkawi',
  '沙巴': 'Sabah',
  '新山': 'Johor_Bahru',
  '热浪岛': 'Redang_Island',
  '会安': 'Hội_An',
  '大叻': 'Đà_Lạt',
  '富国岛': 'Phú_Quốc',
  '美奈': 'Mũi_Né',
  '顺化': 'Huế',
  '龙目岛': 'Lombok',
  '科莫多岛': 'Komodo_Island',
  '民丹岛': 'Bintan_Island',
  '宿务': 'Cebu',
  '薄荷岛': 'Bohol',
  '巴拉望': 'Palawan',
  '锡亚高': 'Siargao',
  '爱妮岛': 'El_Nido,_Palawan',
  '马累': 'Malé',
  '加勒': 'Galle',
  '曼德勒': 'Mandalay',
  '蒲甘': 'Bagan',
  '哥德堡': 'Gothenburg',
  '基律纳': 'Kiruna',
  '罗瓦涅米': 'Rovaniemi',
  '海牙': 'The_Hague',
  '代尔夫特': 'Delft',
  '辛特拉': 'Sintra',
  '法鲁': 'Faro,_Portugal',
  '马德拉群岛': 'Madeira',
  '巴拉顿湖': 'Lake_Balaton',
  '克拉科夫': 'Kraków',
  '布鲁日': 'Bruges',
  '卡尔加里': 'Calgary',
  '维多利亚': 'Victoria,_British_Columbia',
  '贾斯珀': 'Jasper,_Alberta',
  '惠斯勒': 'Whistler,_British_Columbia',
  '圣地亚哥': 'San_Diego',
  '瓜达拉哈拉': 'Guadalajara',
  '霍巴特': 'Hobart',
  '达尔文': 'Darwin,_Northern_Territory',
  '袋鼠岛': 'Kangaroo_Island',
  '蒂卡波湖': 'Lake_Tekapo',
  '福克斯冰川': 'Fox_Glacier',
  '水母湖': 'Jellyfish_Lake',
  '德雷克海峡': 'Drake_Passage',
  '阿斯旺': 'Aswan',
  '锡瓦绿洲': 'Siwa_Oasis',
  '菲斯': 'Fes',
  '丹吉尔': 'Tangier',
  '突尼斯城': 'Tunis',
  '温得和克': 'Windhoek',
  '路易港': 'Port_Louis',
  '楠迪': 'Nadi',
  '埃尔卡拉法特': 'El_Calafate',
  '索伦托': 'Sorrento',
  '平遥': 'Pingyao',
  '五台山': 'Mount_Wutai',
  '壶口瀑布': 'Hukou_Waterfall',
  '武夷山': 'Mount_Wuyi',
  '平潭': 'Pingtan_Island',
  '九华山': 'Mount_Jiuhua',
  '庐山': 'Mount_Lu',
  '婺源': 'Wuyuan_County,_Jiangxi',
  '井冈山': 'Jinggangshan',
  '三清山': 'Mount_Sanqing',
  '曲阜': 'Qufu',
  '济源': 'Jiyuan',
  '雄安新区': 'Xiong%27an_New_Area',
  '长白山': 'Changbai_Mountains',
  '漠河': 'Mohe_City',
  '雪乡': 'China_Snow_Town',
  '亚布力': 'Yabuli',
  '华山': 'Mount_Hua',
  '腾冲': 'Tengchong',
  '喀纳斯': 'Kanas_Lake',
  '那拉提': 'Narat_Grassland',
  '赛里木湖': 'Sayram_Lake',
  '珠峰大本营': 'Everest_Base_Camp',
  '纳木错': 'Lake_Namtso',
  '羊卓雍措': 'Yamdrok_Lake',
  '雅鲁藏布大峡谷': 'Yarlung_Tsangpo_Grand_Canyon',
  '青海湖': 'Qinghai_Lake',
  '茶卡盐湖': 'Chaka_Salt_Lake',
  '祁连': 'Qilian_County',
  '沙坡头': 'Shapotou_District',
  '贺兰山': 'Helan_Mountains',
  '黄果树': 'Huangguoshu_Waterfall',
  '梵净山': 'Fanjingshan',
  '阳朔': 'Yangshuo_County',
  '涠洲岛': 'Weizhou_Island',
  '凤凰': 'Fenghuang_County',
  '凯里': 'Kaili_City',
  '镇远': 'Zhenyuan_County,_Guizhou',
  '荔波': 'Libo_County',
  '吉首': 'Jishou',
  '满洲里': 'Manzhouli',
  '阿尔山': 'Arxan',
  '额济纳': 'Ejin_Banner',
}

// ==================== 核心函数 ====================

/** 从 Wikipedia 获取城市图片 */
async function fetchWikiImage(wikiName) {
  const urls = [
    `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(wikiName)}`,
    `https://en.wikipedia.org/api/rest_v1/page/media/${encodeURIComponent(wikiName)}`,
  ]

  for (const url of urls) {
    try {
      const resp = await axios.get(url, {
        headers: { 'User-Agent': 'TravelApp/1.0 (image-scraper; contact@example.com)' },
        timeout: 10000,
      })
      const data = resp.data

      // /page/summary 返回的格式
      if (data.thumbnail && data.thumbnail.source) {
        return data.thumbnail.source
      }
      // /page/summary 的 originalimage
      if (data.originalimage && data.originalimage.source) {
        return data.originalimage.source
      }
      // /page/media 返回格式
      if (data.items && data.items.length > 0) {
        for (const item of data.items) {
          if (item.type === 'image' && item.showInGallery !== false) {
            return item.srcset?.[0]?.src || item.src || ''
          }
        }
      }
    } catch {}
  }
  return null
}

/** 从百度百科获取图片（国内城市） */
async function fetchBaiduBaikeImage(cityName) {
  try {
    const searchUrl = `https://baike.baidu.com/item/${encodeURIComponent(cityName)}`
    const resp = await axios.get(searchUrl, {
      headers: {
        'User-Agent': USER_AGENT,
        'Accept': 'text/html,application/xhtml+xml',
        'Accept-Language': 'zh-CN,zh;q=0.9',
      },
      timeout: 10000,
    })

    const $ = cheerio.load(resp.data)
    // 百度百科的 summary-pic 类
    const img = $('.summary-pic img')
    if (img.length > 0) {
      const src = img.attr('src')
      if (src && src.startsWith('http') && !src.includes('baike-bos')) {
        return src
      }
    }

    // 尝试 .lemma-picture img
    const lemmaImg = $('.lemma-picture img')
    if (lemmaImg.length > 0) {
      const src = lemmaImg.attr('src')
      if (src && src.startsWith('http')) return src
    }
  } catch {}
  return null
}

/** 判断图片 URL 是否有意义 */
function isValidImageUrl(url) {
  if (!url || !url.startsWith('http')) return false
  const l = url.toLowerCase()

  // 过滤 SVG/图标/小图
  if (l.endsWith('.svg') || l.endsWith('.gif')) return false
  if (l.includes('icon') || l.includes('logo') || l.includes('avatar')) return false
  if (l.includes('20px') || l.includes('30px') || l.includes('50px')) return false

  // Wikipedia 的 SVG 渲染不应使用
  if (l.includes('wikipedia/') && l.endsWith('.svg.png')) return false

  for (const p of BLOCKED_PATTERNS) {
    if (l.includes(p)) return false
  }

  return true
}

/** 高精度评分 */
function scoreImageUrl(url, cityName) {
  if (!url || !url.startsWith('http')) return 0
  const l = url.toLowerCase()

  for (const p of BLOCKED_PATTERNS) {
    if (l.includes(p)) return 0
  }

  // Wikipedia/Wikimedia 图片质量好
  if (l.includes('upload.wikimedia.org') || l.includes('commons.wikimedia.org')) return 95

  for (const d of GOOD_DOMAINS) {
    if (l.includes(d)) return 85
  }

  let score = 20
  if (url.length < 200) score += 10
  if (url.length > 600) score -= 10
  if (l.endsWith('.gif') || l.endsWith('.svg')) score -= 50
  if (l.includes('.jpg') || l.includes('.jpeg') || l.includes('.png') || l.includes('.webp')) score += 5
  if (l.includes('photo') || l.includes('image') || l.includes('travel') || l.includes('scenic') || l.includes('view')) score += 10

  return Math.max(0, Math.min(100, score))
}

/** 从 Bing 搜索获取图片（兜底） */
async function scrapeBing(cityName) {
  const queries = [
    `${encodeURIComponent(cityName)} 旅游景点 风景`,
    `${encodeURIComponent(cityName)} city travel landscape`,
    `${encodeURIComponent(cityName)} 城市 地标`,
  ]

  const allImages = []
  for (const query of queries) {
    if (allImages.length >= 10) break
    try {
      const resp = await axios.get(`https://cn.bing.com/images/search?q=${query}&first=1`, {
        headers: {
          'User-Agent': USER_AGENT,
          'Accept': 'text/html,application/xhtml+xml',
          'Accept-Language': 'zh-CN,zh;q=0.9',
        },
        timeout: 15000,
      })

      const $ = cheerio.load(resp.data)
      $('a.iusc').each((_, el) => {
        try {
          const m = $(el).attr('m')
          if (m) {
            const parsed = JSON.parse(m)
            if (parsed.murl) allImages.push({ url: parsed.murl, title: parsed.t || '' })
          }
        } catch {}
      })
    } catch {}
  }

  if (allImages.length === 0) return null

  const scored = allImages
    .map(img => ({ ...img, score: scoreImageUrl(img.url, cityName) }))
    .filter(img => img.score >= 25)
    .sort((a, b) => b.score - a.score)

  return scored.length > 0 ? scored[0].url : null
}

/** 主函数：获取单个城市的图片 */
async function getCityImage(cityName) {
  // 1. 尝试 Wikipedia（有映射用映射，没有直接搜）
  const wikiName = WIKI_OVERRIDES[cityName] || cityName
  const wikiUrl = await fetchWikiImage(wikiName)
  if (wikiUrl && isValidImageUrl(wikiUrl)) {
    return { url: wikiUrl, source: 'wikipedia' }
  }

  // 如果没有映射，尝试直接用中文名搜 Wikipedia
  if (!WIKI_OVERRIDES[cityName]) {
    const cnWikiUrl = await fetchWikiImage(cityName)
    if (cnWikiUrl && isValidImageUrl(cnWikiUrl)) {
      return { url: cnWikiUrl, source: 'wikipedia-cn' }
    }
  }

  // 2. 尝试百度百科
  const baiduUrl = await fetchBaiduBaikeImage(cityName)
  if (baiduUrl && isValidImageUrl(baiduUrl)) {
    return { url: baiduUrl, source: 'baidu-baike' }
  }

  // 3. Bing 兜底
  const bingUrl = await scrapeBing(cityName)
  if (bingUrl) {
    return { url: bingUrl, source: 'bing' }
  }

  return null
}

// ==================== 主流程 ====================
const existing = {}
if (fs.existsSync(OUTPUT)) {
  try { Object.assign(existing, JSON.parse(fs.readFileSync(OUTPUT, 'utf-8'))) } catch {}
}

// 检测低质量图片需要重新爬取
const NEED_RESCUE = new Set()
for (const [city, url] of Object.entries(existing)) {
  if (url && scoreImageUrl(url, city) < 30) {
    NEED_RESCUE.add(city)
    delete existing[city]
  }
  // 空字符串也重新爬
  if (!url || url.trim() === '') {
    NEED_RESCUE.add(city)
    delete existing[city]
  }
}
if (NEED_RESCUE.size > 0) {
  console.log(`检测到 ${NEED_RESCUE.size} 条低质量/空图片，将重新爬取`)
}

async function main() {
  const allCities = [...new Set([...NEED_RESCUE, ...UNIQUE_CITIES])]
  const cities = allCities.filter(c => !existing[c] || !existing[c])
  console.log(`共 ${allCities.length} 个城市，已有 ${Object.keys(existing).filter(k => existing[k]).length} 张合格图片，待爬 ${cities.length} 个\n`)

  let count = 0; let goodCount = 0; let wikiCount = 0; let baiduCount = 0; let bingCount = 0
  for (const city of cities) {
    process.stdout.write(`[${count + 1}/${cities.length}] ${city} ... `)

    const result = await getCityImage(city)
    if (result && result.url && scoreImageUrl(result.url, city) >= 25) {
      existing[city] = result.url
      goodCount++
      if (result.source === 'wikipedia' || result.source === 'wikipedia-cn') wikiCount++
      else if (result.source === 'baidu-baike') baiduCount++
      else bingCount++
      console.log(`✓ ${result.source}`)
    } else {
      existing[city] = ''
      console.log(`✗`)
    }

    count++
    if (count % 20 === 0) {
      fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
      console.log(`  → 已保存 | 合格 ${goodCount}/${count} | Wiki:${wikiCount} 百度:${baiduCount} Bing:${bingCount}\n`)
    }

    await new Promise(r => setTimeout(r, DELAY_MS + Math.random() * 400))
  }

  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, '\t'))
  const valid = Object.values(existing).filter(Boolean).length
  console.log(`\n====================`)
  console.log(`完成！${valid}/${Object.keys(existing).length} 有图片`)
  console.log(`来源：Wiki ${wikiCount} | 百度 ${baiduCount} | Bing ${bingCount}`)
  console.log(`输出: ${OUTPUT}`)
}

main().catch(console.error)
