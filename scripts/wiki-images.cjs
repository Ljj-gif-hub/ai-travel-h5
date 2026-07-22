/**
 * 从 Wikipedia 获取城市官方主图 — 人工审核过的最可靠来源
 * 用法: node scripts/wiki-images.cjs
 */
const axios = require('axios')
const fs = require('fs')
const path = require('path')

const JSON_PATH = path.join(__dirname, '..', 'public', 'city-images.json')
const existing = fs.existsSync(JSON_PATH) ? JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8')) : {}

// 城市名 → 英文/日文 Wiki 页面名
const WIKI_NAMES = {
  '北京':'Beijing','上海':'Shanghai','天津':'Tianjin','重庆':'Chongqing',
  '广州':'Guangzhou','深圳':'Shenzhen','珠海':'Zhuhai','东莞':'Dongguan','佛山':'Foshan','中山':'Zhongshan','惠州':'Huizhou','汕头':'Shantou','江门':'Jiangmen','湛江':'Zhanjiang','茂名':'Maoming','肇庆':'Zhaoqing','梅州':'Meizhou','韶关':'Shaoguan',
  '杭州':'Hangzhou','宁波':'Ningbo','温州':'Wenzhou','嘉兴':'Jiaxing','湖州':'Huzhou','绍兴':'Shaoxing','金华':'Jinhua','舟山':'Zhoushan','台州':'Taizhou,_Zhejiang','丽水':'Lishui,_Zhejiang',
  '南京':'Nanjing','苏州':'Suzhou','无锡':'Wuxi','常州':'Changzhou','南通':'Nantong','扬州':'Yangzhou','镇江':'Zhenjiang','徐州':'Xuzhou','盐城':'Yancheng','连云港':'Lianyungang','宿迁':'Suqian',
  '成都':'Chengdu','绵阳':'Mianyang','德阳':'Deyang','宜宾':'Yibin','南充':'Nanchong','泸州':'Luzhou','乐山':'Leshan','眉山':'Meishan','自贡':'Zigong','攀枝花':'Panzhihua','广元':'Guangyuan','内江':'Neijiang','广安':"Guang'an",'达州':'Dazhou,_Sichuan','雅安':"Ya'an",
  '武汉':'Wuhan','宜昌':'Yichang','襄阳':'Xiangyang','荆州':'Jingzhou','黄石':'Huangshi','十堰':'Shiyan','孝感':'Xiaogan','黄冈':'Huanggang,_Hubei','咸宁':'Xianning','恩施':'Enshi_City','荆门':'Jingmen','神农架':'Shennongjia',
  '长沙':'Changsha','株洲':'Zhuzhou','湘潭':'Xiangtan','衡阳':'Hengyang','岳阳':'Yueyang','常德':'Changde','张家界':'Zhangjiajie','郴州':'Chenzhou','益阳':'Yiyang','永州':'Yongzhou','怀化':'Huaihua','娄底':'Loudi','邵阳':'Shaoyang','凤凰':'Fenghuang_County',
  '福州':'Fuzhou','厦门':'Xiamen','泉州':'Quanzhou','漳州':'Zhangzhou','莆田':'Putian','龙岩':'Longyan','南平':'Nanping','宁德':'Ningde','武夷山':'Mount_Wuyi',
  '济南':'Jinan','青岛':'Qingdao','烟台':'Yantai','威海':'Weihai','潍坊':'Weifang','淄博':'Zibo','临沂':'Linyi','济宁':'Jining','泰安':"Tai'an",'日照':'Rizhao','德州':'Dezhou','聊城':'Liaocheng','滨州':'Binzhou','菏泽':'Heze','曲阜':'Qufu',
  '郑州':'Zhengzhou','洛阳':'Luoyang','开封':'Kaifeng','南阳':'Nanyang,_Henan','许昌':'Xuchang','安阳':'Anyang','信阳':'Xinyang',
  '石家庄':'Shijiazhuang','唐山':'Tangshan','保定':'Baoding','邯郸':'Handan','廊坊':'Langfang','沧州':'Cangzhou','秦皇岛':'Qinhuangdao','张家口':'Zhangjiakou','承德':'Chengde','衡水':'Hengshui',
  '沈阳':'Shenyang','大连':'Dalian','鞍山':"Anshan",'抚顺':'Fushun','本溪':'Benxi','丹东':'Dandong','锦州':'Jinzhou',
  '西安':"Xi'an",'咸阳':'Xianyang','宝鸡':'Baoji','渭南':'Weinan','延安':"Yan'an",'汉中':'Hanzhong','榆林':'Yulin,_Shaanxi','华山':'Mount_Hua',
  '昆明':'Kunming','大理':'Dali_City','丽江':'Lijiang','香格里拉':'Shangri-La_City','西双版纳':'Xishuangbanna_Dai_Autonomous_Prefecture','腾冲':'Tengchong','普洱':"Pu'er_City",'曲靖':'Qujing','玉溪':'Yuxi',
  '贵阳':'Guiyang','遵义':'Zunyi','安顺':'Anshun','毕节':'Bijie','铜仁':'Tongren','凯里':'Kaili_City','黄果树':'Huangguoshu_Waterfall',
  '南宁':'Nanning','桂林':'Guilin','柳州':'Liuzhou','北海':'Beihai','阳朔':'Yangshuo_County','涠洲岛':'Weizhou_Island',
  '海口':'Haikou','三亚':'Sanya','儋州':'Danzhou','琼海':'Qionghai','文昌':'Wenchang','万宁':'Wanning,_Hainan',
  '合肥':'Hefei','芜湖':'Wuhu','蚌埠':'Bengbu','安庆':'Anqing','黄山':'Huangshan','阜阳':'Fuyang','九华山':'Mount_Jiuhua',
  '南昌':'Nanchang','九江':'Jiujiang','景德镇':'Jingdezhen','赣州':'Ganzhou','上饶':'Shangrao','宜春':'Yichun,_Jiangxi','吉安':"Ji'an",'庐山':'Mount_Lu','婺源':'Wuyuan_County,_Jiangxi','三清山':'Mount_Sanqing',
  '太原':'Taiyuan','大同':'Datong','长治':'Changzhi','临汾':'Linfen','运城':'Yuncheng','平遥':'Pingyao','五台山':'Mount_Wutai',
  '长春':'Changchun','吉林':'Jilin_City','延边':'Yanbian_Korean_Autonomous_Prefecture','长白山':'Changbai_Mountains',
  '哈尔滨':'Harbin','齐齐哈尔':'Qiqihar','牡丹江':'Mudanjiang','佳木斯':'Jiamusi','大庆':'Daqing','漠河':'Mohe_City','雪乡':'China_Snow_Town',
  '兰州':'Lanzhou','嘉峪关':'Jiayuguan_City','天水':'Tianshui','张掖':'Zhangye','酒泉':'Jiuquan','敦煌':'Dunhuang',
  '呼和浩特':'Hohhot','包头':'Baotou','鄂尔多斯':'Ordos_City','呼伦贝尔':'Hulunbuir','赤峰':'Chifeng','满洲里':'Manzhouli',
  '乌鲁木齐':'Urumqi','吐鲁番':'Turpan','哈密':'Hami','喀什':'Kashgar','伊犁':'Ili_Kazakh_Autonomous_Prefecture','阿勒泰':'Altay_City','库尔勒':'Korla','喀纳斯':'Kanas_Lake',
  '拉萨':'Lhasa','日喀则':'Shigatse','林芝':'Nyingchi','阿里':'Ngari_Prefecture','纳木错':'Namtso',
  '西宁':'Xining','海东':'Haidong','格尔木':'Golmud','青海湖':'Qinghai_Lake','茶卡盐湖':'Chaka_Salt_Lake',
  '银川':'Yinchuan','中卫':'Zhongwei','沙坡头':'Shapotou_District',
  '香港':'Hong_Kong','澳门':'Macau',
  '台北':'Taipei','新北':'New_Taipei_City','高雄':'Kaohsiung','台中':'Taichung','台南':'Tainan','桃园':'Taoyuan,_Taiwan','花莲':'Hualien_City','台东':'Taitung_City','宜兰':'Yilan_City','南投':'Nantou_City','嘉义':'Chiayi','屏东':'Pingtung_City','苗栗':'Miaoli_City','新竹':'Hsinchu','彰化':'Changhua_City','云林':'Yunlin_County','澎湖':'Penghu','金门':'Kinmen','马祖':'Matsu_Islands','基隆':'Keelung',
  '日月潭':'Sun_Moon_Lake','阿里山':'Alishan_National_Scenic_Area','太鲁阁':'Taroko_National_Park',
  '东京':'Tokyo','大阪':'Osaka','京都':'Kyoto','札幌':'Sapporo','福冈':'Fukuoka','名古屋':'Nagoya','神户':'Kobe','奈良':'Nara,_Nara','冲绳':'Okinawa_Island','横滨':'Yokohama','箱根':'Hakone','富士山':'Mount_Fuji',
  '首尔':'Seoul','釜山':'Busan','济州':'Jeju_Island','庆州':'Gyeongju',
  '曼谷':'Bangkok','清迈':'Chiang_Mai','普吉岛':'Phuket','芭提雅':'Pattaya','苏梅岛':'Ko_Samui',
  '新加坡':'Singapore',
  '吉隆坡':'Kuala_Lumpur','槟城':'Penang','马六甲':'Malacca_City','兰卡威':'Langkawi','仙本那':'Semporna',
  '河内':'Hanoi','胡志明市':'Ho_Chi_Minh_City','岘港':'Da_Nang','芽庄':'Nha_Trang','会安':'Hoi_An','下龙湾':'Ha_Long_Bay',
  '巴厘岛':'Bali','雅加达':'Jakarta','龙目岛':'Lombok','科莫多岛':'Komodo_(island)',
  '马尼拉':'Manila','长滩岛':'Boracay','宿务':'Cebu_City',
  '迪拜':'Dubai','阿布扎比':'Abu_Dhabi',
  '新德里':'New_Delhi','孟买':'Mumbai','阿格拉':'Agra','斋浦尔':'Jaipur','瓦拉纳西':'Varanasi',
  '金边':'Phnom_Penh','暹粒':'Siem_Reap','吴哥窟':'Angkor_Wat',
  '马尔代夫':'Maldives',
  '科伦坡':'Colombo','琅勃拉邦':'Luang_Prabang','万象':'Vientiane','加德满都':'Kathmandu',
  '伊斯坦布尔':'Istanbul','卡帕多奇亚':'Cappadocia','安塔利亚':'Antalya','棉花堡':'Pamukkale',
  '巴黎':'Paris','尼斯':'Nice','里昂':'Lyon','马赛':'Marseille','波尔多':'Bordeaux','戛纳':'Cannes','普罗旺斯':'Provence','霞慕尼':'Chamonix',
  '罗马':'Rome','威尼斯':'Venice','米兰':'Milan','佛罗伦萨':'Florence','比萨':'Pisa','五渔村':'Cinque_Terre','科莫湖':'Lake_Como',
  '伦敦':'London','爱丁堡':'Edinburgh','曼彻斯特':'Manchester','利物浦':'Liverpool','牛津':'Oxford','剑桥':'Cambridge','巴斯':'Bath,_Somerset','约克':'York','格拉斯哥':'Glasgow','巨石阵':'Stonehenge',
  '柏林':'Berlin','慕尼黑':'Munich','法兰克福':'Frankfurt','汉堡':'Hamburg','科隆':'Cologne','海德堡':'Heidelberg','新天鹅堡':'Neuschwanstein_Castle',
  '巴塞罗那':'Barcelona','马德里':'Madrid','塞维利亚':'Seville','格拉纳达':'Granada','瓦伦西亚':'Valencia',
  '苏黎世':'Zurich','日内瓦':'Geneva','卢塞恩':'Lucerne','因特拉肯':'Interlaken','采尔马特':'Zermatt',
  '雅典':'Athens','圣托里尼':'Santorini','米克诺斯':'Mykonos',
  '阿姆斯特丹':'Amsterdam','鹿特丹':'Rotterdam','羊角村':'Giethoorn',
  '布拉格':'Prague','维也纳':'Vienna','萨尔茨堡':'Salzburg',
  '雷克雅未克':'Reykjavik','奥斯陆':'Oslo','卑尔根':'Bergen',
  '斯德哥尔摩':'Stockholm','赫尔辛基':'Helsinki','哥本哈根':'Copenhagen',
  '里斯本':'Lisbon','波尔图':'Porto','布达佩斯':'Budapest','杜布罗夫尼克':'Dubrovnik',
  '华沙':'Warsaw','克拉科夫':'Krakow','都柏林':'Dublin','布鲁塞尔':'Brussels','布鲁日':'Bruges',
  '莫斯科':'Moscow','圣彼得堡':'Saint_Petersburg','贝加尔湖':'Lake_Baikal',
  '纽约':'New_York_City','洛杉矶':'Los_Angeles','旧金山':'San_Francisco','拉斯维加斯':'Las_Vegas','芝加哥':'Chicago','华盛顿':'Washington,_D.C.','波士顿':'Boston','西雅图':'Seattle','迈阿密':'Miami','夏威夷':'Hawaii',
  '多伦多':'Toronto','温哥华':'Vancouver','蒙特利尔':'Montreal','渥太华':'Ottawa','魁北克':'Quebec_City','班夫':'Banff_National_Park','尼亚加拉瀑布':'Niagara_Falls',
  '墨西哥城':'Mexico_City','坎昆':'Cancun','哈瓦那':'Havana',
  '里约热内卢':'Rio_de_Janeiro','圣保罗':'Sao_Paulo','布宜诺斯艾利斯':'Buenos_Aires','乌斯怀亚':'Ushuaia',
  '马丘比丘':'Machu_Picchu','加拉帕戈斯群岛':'Galapagos_Islands','复活节岛':'Easter_Island',
  '悉尼':'Sydney','墨尔本':'Melbourne','黄金海岸':'Gold_Coast,_Queensland','布里斯班':'Brisbane','珀斯':'Perth','凯恩斯':'Cairns',
  '奥克兰':'Auckland','皇后镇':'Queenstown,_New_Zealand','基督城':'Christchurch','惠灵顿':'Wellington','米尔福德峡湾':'Milford_Sound',
  '波拉波拉岛':'Bora_Bora',
  '开罗':'Cairo','卢克索':'Luxor','阿斯旺':'Aswan','马拉喀什':'Marrakesh','卡萨布兰卡':'Casablanca','撒哈拉':'Sahara',
  '开普敦':'Cape_Town','约翰内斯堡':'Johannesburg','克鲁格国家公园':'Kruger_National_Park',
  '内罗毕':'Nairobi','桑给巴尔':'Zanzibar','塞伦盖蒂':'Serengeti','乞力马扎罗':'Mount_Kilimanjaro',
  '南极半岛':'Antarctic_Peninsula',
}

async function getWikiImage(wikiName) {
  if (!wikiName) return null
  try {
    const url = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(wikiName)}`
    const resp = await axios.get(url, { timeout: 10000, headers: { 'User-Agent': 'TravelApp/1.0' } })
    const data = resp.data
    if (data.originalimage?.source) return data.originalimage.source
    if (data.thumbnail?.source) return data.thumbnail.source
  } catch {}
  // 尝试中文 Wikipedia
  try {
    const url = `https://zh.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(wikiName)}`
    const resp = await axios.get(url, { timeout: 10000, headers: { 'User-Agent': 'TravelApp/1.0' } })
    const data = resp.data
    if (data.originalimage?.source) return data.originalimage.source
    if (data.thumbnail?.source) return data.thumbnail.source
  } catch {}
  return null
}

async function main() {
  const cities = Object.keys(WIKI_NAMES)
  let ok = 0, fail = 0

  for (let i = 0; i < cities.length; i++) {
    const city = cities[i]
    const wikiName = WIKI_NAMES[city]
    process.stdout.write(`[${i+1}/${cities.length}] ${city} (${wikiName}) ... `)

    const img = await getWikiImage(wikiName)
    if (img) {
      existing[city] = img
      ok++
      console.log(`✓ ${img.substring(0, 70)}...`)
    } else {
      fail++
      console.log('✗')
    }

    if ((i+1) % 20 === 0) {
      fs.writeFileSync(JSON_PATH, JSON.stringify(existing, null, 2))
      console.log(`  → 已保存 | 成功${ok} 失败${fail}\n`)
    }
    await new Promise(r => setTimeout(r, 300))
  }

  fs.writeFileSync(JSON_PATH, JSON.stringify(existing, null, 2))
  console.log(`\n完成！${cities.length}个城市，Wikipedia命中${ok}个，未命中${fail}个`)
  console.log(`输出: ${JSON_PATH}`)
}

main().catch(console.error)
