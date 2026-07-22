/**
 * 爬取 → 评分 → 直接入库 city_material
 * 最高分策略：多关键词 + 严格黑名单 + 高门槛 + 优选域名
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const API = 'http://localhost:3200/api/city/materials/batch'
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36'

// ====== 城市列表 ======
const CITIES = [
  '北京','上海','天津','重庆','香港','澳门',
  '广州','深圳','珠海','东莞','佛山','中山','惠州','汕头','江门','湛江','茂名','肇庆','梅州','汕尾','河源','阳江','清远','潮州','揭阳','云浮','韶关',
  '杭州','宁波','温州','嘉兴','湖州','绍兴','金华','舟山','衢州','台州','丽水',
  '南京','苏州','无锡','常州','南通','扬州','镇江','徐州','盐城','泰州','淮安','连云港','宿迁',
  '成都','绵阳','德阳','宜宾','南充','泸州','乐山','眉山','自贡','攀枝花','广元','遂宁','内江','广安','达州','雅安','巴中','资阳','阿坝','甘孜','凉山',
  '武汉','宜昌','襄阳','荆州','黄石','十堰','鄂州','孝感','黄冈','咸宁','随州','恩施','荆门','神农架',
  '长沙','株洲','湘潭','衡阳','岳阳','常德','张家界','郴州','益阳','永州','怀化','娄底','邵阳','凤凰',
  '福州','厦门','泉州','漳州','莆田','龙岩','三明','南平','宁德','武夷山',
  '济南','青岛','烟台','威海','潍坊','淄博','临沂','济宁','泰安','日照','德州','聊城','滨州','菏泽','曲阜',
  '郑州','洛阳','开封','南阳','许昌','新乡','安阳','信阳','商丘','周口','驻马店','平顶山','焦作',
  '石家庄','唐山','保定','邯郸','廊坊','沧州','秦皇岛','张家口','承德','邢台','衡水',
  '沈阳','大连','鞍山','抚顺','本溪','丹东','锦州','营口',
  '西安','咸阳','宝鸡','渭南','延安','汉中','榆林','安康','华山',
  '昆明','大理','丽江','香格里拉','西双版纳','腾冲','普洱','曲靖','玉溪',
  '贵阳','遵义','安顺','毕节','铜仁','凯里','镇远','荔波','黄果树','梵净山',
  '南宁','桂林','柳州','北海','阳朔','涠洲岛',
  '海口','三亚','儋州','琼海','文昌','万宁',
  '合肥','芜湖','蚌埠','安庆','黄山','阜阳','九华山',
  '南昌','九江','景德镇','赣州','上饶','宜春','吉安','庐山','婺源','井冈山','三清山',
  '太原','大同','长治','临汾','运城','平遥','五台山','壶口瀑布',
  '长春','吉林','延边','长白山',
  '哈尔滨','齐齐哈尔','牡丹江','佳木斯','大庆','漠河','雪乡','亚布力',
  '兰州','嘉峪关','天水','武威','张掖','酒泉','敦煌',
  '呼和浩特','包头','鄂尔多斯','呼伦贝尔','赤峰','满洲里','阿尔山','额济纳',
  '乌鲁木齐','吐鲁番','哈密','喀什','伊犁','阿勒泰','库尔勒','喀纳斯','那拉提','赛里木湖',
  '拉萨','日喀则','林芝','阿里','纳木错','羊卓雍措',
  '西宁','海东','格尔木','德令哈','青海湖','茶卡盐湖','祁连',
  '银川','中卫','沙坡头','贺兰山',
  '台北','新北','高雄','台中','台南','桃园','花莲','台东','宜兰','南投','嘉义','屏东','苗栗','新竹','彰化','云林','澎湖','金门','马祖','基隆',
  '日月潭','阿里山','垦丁','太鲁阁','九份','淡水',
  '东京','大阪','京都','札幌','福冈','名古屋','神户','奈良','冲绳','横滨','箱根','富士山',
  '首尔','釜山','济州','庆州',
  '曼谷','清迈','普吉岛','芭提雅','苏梅岛','甲米','华欣','清莱','拜县',
  '新加坡','吉隆坡','槟城','马六甲','兰卡威','沙巴','仙本那',
  '河内','胡志明市','岘港','芽庄','会安','下龙湾','美奈',
  '巴厘岛','雅加达','龙目岛','科莫多岛',
  '马尼拉','长滩岛','宿务','薄荷岛','巴拉望',
  '迪拜','阿布扎比','马尔代夫',
  '新德里','孟买','阿格拉','斋浦尔','瓦拉纳西',
  '金边','暹粒','吴哥窟','琅勃拉邦','万象','加德满都','博卡拉',
  '伊斯坦布尔','卡帕多奇亚','安塔利亚','棉花堡','耶路撒冷','佩特拉','死海',
  '巴黎','尼斯','里昂','马赛','波尔多','戛纳','阿维尼翁','普罗旺斯','霞慕尼',
  '罗马','威尼斯','米兰','佛罗伦萨','比萨','五渔村','科莫湖','多洛米蒂','西西里',
  '伦敦','爱丁堡','曼彻斯特','利物浦','牛津','剑桥','巴斯','约克','天空岛','巨石阵',
  '柏林','慕尼黑','法兰克福','汉堡','科隆','海德堡','斯图加特','新天鹅堡','国王湖',
  '巴塞罗那','马德里','塞维利亚','格拉纳达','瓦伦西亚','龙达','马略卡岛',
  '苏黎世','日内瓦','卢塞恩','因特拉肯','采尔马特','少女峰','蒙特勒',
  '雅典','圣托里尼','米克诺斯','克里特','罗德岛','扎金索斯',
  '阿姆斯特丹','鹿特丹','羊角村','库肯霍夫',
  '布拉格','克鲁姆洛夫','维也纳','萨尔茨堡','哈尔施塔特','因斯布鲁克',
  '雷克雅未克','蓝湖','冰河湖','奥斯陆','卑尔根','特罗姆瑟','罗弗敦群岛',
  '斯德哥尔摩','赫尔辛基','罗瓦涅米','圣诞老人村','哥本哈根',
  '里斯本','波尔图','布达佩斯','杜布罗夫尼克','十六湖',
  '华沙','克拉科夫','都柏林','莫赫悬崖','布鲁塞尔','布鲁日',
  '莫斯科','圣彼得堡','贝加尔湖','摩尔曼斯克',
  '纽约','洛杉矶','旧金山','拉斯维加斯','芝加哥','华盛顿','波士顿','西雅图','迈阿密','奥兰多','夏威夷',
  '多伦多','温哥华','蒙特利尔','渥太华','魁北克','班夫','尼亚加拉瀑布',
  '墨西哥城','坎昆','哈瓦那',
  '里约热内卢','圣保罗','布宜诺斯艾利斯','乌斯怀亚','埃尔卡拉法特',
  '马丘比丘','加拉帕戈斯群岛','复活节岛','百内国家公园',
  '悉尼','墨尔本','黄金海岸','布里斯班','珀斯','凯恩斯','大堡礁','大洋路',
  '奥克兰','皇后镇','基督城','惠灵顿','米尔福德峡湾','库克山','霍比屯','蒂卡波湖',
  '波拉波拉岛','南极半岛','德雷克海峡',
  '开罗','卢克索','阿斯旺','阿布辛贝','马拉喀什','卡萨布兰卡','菲斯','舍夫沙万','撒哈拉',
  '开普敦','约翰内斯堡','克鲁格国家公园','内罗毕','马赛马拉','桑给巴尔','塞伦盖蒂','乞力马扎罗',
  '路易港','蓝湾','突尼斯城','蓝白小镇',
]
const ALL = [...new Set(CITIES)]

// ====== 严格评分 ======
const BLOCK = ['icon','logo','avatar','chart','graph','barcode','qr','thumb','product','mall','shop','taobao','jd.','alibaba','walmart','amazon.','ebay.','etsy.','shopify','dress','clothing','shirt','shoe','sneaker','jewelry','watch','perfume','cosmetic','furniture','appliance','hardware','mapamundi','politico','nombres','breed','identifier','font.','wallpaper-4k','desktop-wallpaper','discount','coupon','banner','screenshot','diagram','infographic','passport','food/','recipe','game/','meme','box','package','carton','cat-breed','chartfarosh','travelfashiongirl','dmjnb','hanyu-word','calligraphy','stroke-order','fantaslook','ruffle','floral','sundress','optmv','sunsight','png?','.png?','seo/','spider','crawl','.gif','.svg']

const PREFER = ['wikipedia','wikimedia','flickr','unsplash','pexels','pixabay','zhimg','bcebos','699pic','ctrip','qunar','mafengwo','gettyimages','shutterstock','500px','baidu.com']

function score(u) {
  const l = u.toLowerCase()
  for (const b of BLOCK) if (l.includes(b)) return 0
  let s = 25
  for (const p of PREFER) if (l.includes(p)) { s = 70; break }
  if (u.length < 150) s += 10
  if (u.length > 500) s -= 10
  return Math.max(0, Math.min(100, s))
}

async function scrape(city) {
  const q = encodeURIComponent(`${city} 旅游景点 实拍风景`)
  const imgs = []
  for (const host of ['cn.bing.com', 'www.bing.com']) {
    try {
      const r = await axios.get(`https://${host}/images/search?q=${q}&first=1`, {
        headers: { 'User-Agent': UA, 'Accept': 'text/html', 'Accept-Language': 'zh-CN,zh;q=0.9' },
        timeout: 12000,
      })
      const $ = cheerio.load(r.data)
      $('a.iusc').each((_, el) => {
        try { const m = JSON.parse($(el).attr('m') || '{}'); if (m.murl) imgs.push(m.murl) } catch {}
      })
      $('img.mimg').each((_, el) => {
        const s = $(el).attr('src') || $(el).attr('data-src')
        if (s?.startsWith('http') && !imgs.includes(s)) imgs.push(s)
      })
    } catch {}
  }
  return imgs.map(u => ({ url: u, s: score(u) })).filter(x => x.s >= 60).sort((a, b) => b.s - a.s)
}

async function main() {
  console.log(`开始爬取 ${ALL.length} 个城市 (门槛60分)\n`)
  let ok = 0, fail = 0
  const batch = []

  for (let i = 0; i < ALL.length; i++) {
    const city = ALL[i]
    process.stdout.write(`[${i+1}/${ALL.length}] ${city} `)
    const results = await scrape(city)
    if (results.length > 0) {
      batch.push({ cityName: city, imageUrl: results[0].url })
      ok++
      console.log(`✓ ${results[0].s}分`)
    } else {
      fail++
      console.log('✗')
    }

    // 每30条存一次到数据库
    if (batch.length >= 30 || (i === ALL.length - 1 && batch.length > 0)) {
      try {
        await fetch(API, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(batch),
        })
        console.log(`  → 入库 ${batch.length} 条 | ✓${ok} ✗${fail}\n`)
      } catch (e) {
        console.log(`  → 入库失败: ${e.message}\n`)
      }
      batch.length = 0
    }
    await new Promise(r => setTimeout(r, 800 + Math.random() * 400))
  }
  console.log(`\n完成！✓${ok} ✗${fail}`)
}
main().catch(console.error)
