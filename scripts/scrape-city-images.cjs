/**
 * 城市图片爬取脚本
 * 从 Bing 图片搜索爬取每个城市/地区的旅行风景照
 *
 * 用法：node scripts/scrape-city-images.js
 * 输出：public/city-images.json
 *
 * 支持断点续爬：已爬取的城市自动跳过
 */

const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

// ==================== 所有城市列表（全覆盖组件数据） ====================
const CITIES = [
  '东城区','西城区','朝阳区','海淀区','丰台区','石景山区','通州区','大兴区','顺义区','昌平区','房山区','门头沟区','怀柔区','平谷区','密云区','延庆区',
  '浦东新区','黄浦区','徐汇区','长宁区','静安区','虹口区','杨浦区','闵行区','普陀区','宝山区','嘉定区','松江区','青浦区','奉贤区','金山区','崇明区',
  '和平区','河东区','河西区','南开区','河北区','红桥区','滨海新区','武清区','宝坻区','东丽区','西青区','津南区','北辰区','静海区','宁河区','蓟州区',
  '渝中区','江北区','南岸区','沙坪坝区','九龙坡区','渝北区','巴南区','北碚区','大渡口区','涪陵区','万州区','黔江区','长寿区','江津区','合川区','永川区',
  '香港','澳门','中西区','湾仔区','东区','南区','油尖旺区','深水埗区','九龙城区','黄大仙区','观塘区','荃湾区','屯门区','元朗区','北区','大埔区','沙田区','西贡区','离岛区','葵青区',
  '花地玛堂区','圣安多尼堂区','大堂区','望德堂区','风顺堂区','嘉模堂区','路氹填海区','圣方济各堂区',
  '台北','新北','高雄','台中','台南','桃园','花莲','台东','宜兰','南投','嘉义','屏东','苗栗','新竹','彰化','云林','澎湖','金门','马祖','基隆',
  '淡水','九份','十分','阳明山','野柳','美浓','逢甲','高美湿地','关子岭','太鲁阁','七星潭','清水断崖','绿岛','兰屿','知本','礁溪','太平山','日月潭','清境农场','合欢山','阿里山','奋起湖','垦丁','小琉球','司马库斯','鹿港','八卦山','双心石沪',
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
  '罗马','威尼斯','米兰','佛罗伦萨','比萨','五渔村','阿马尔菲','科莫湖','多洛米蒂','西西里','索伦托',
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
// 去重
const UNIQUE_CITIES = [...new Set(CITIES)]

// ==================== 配置 ====================
const OUTPUT = path.join(__dirname, '..', 'public', 'city-images.json')
const DELAY_MS = 1000
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36'

// 黑名单域名/路径关键词（明显不是风景照的来源）
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
]
// 优质域名加分
const GOOD_DOMAINS = [
  'ctrip.com', 'qunar.com', 'tuniu.com', 'mafengwo',
  '699pic.com', 'nipic.com', 'photophoto.cn', 'tuchong.com',
  'pixabay.com', 'pexels.com', 'unsplash.com', 'flickr.com',
  'zhimg.com', 'bcebos.com', 'baidu.com',
  'wikipedia', 'wikimedia',
  'gettyimages', 'shutterstock', 'istockphoto', 'alamy',
]

/** 判断图片URL质量：返回 0-100 分数 */
function scoreImageUrl(url, cityName) {
  if (!url || !url.startsWith('http')) return 0
  const l = url.toLowerCase()

  // 黑名单 → 0分
  for (const p of BLOCKED_PATTERNS) {
    if (l.includes(p)) return 0
  }

  // 优质域名 → 直接85分
  for (const d of GOOD_DOMAINS) {
    if (l.includes(d)) return 85
  }

  // 未知域名 → 低基础分，必须靠其他指标拉分
  let score = 25
  if (url.length < 200) score += 10
  if (url.length > 600) score -= 10
  if (l.endsWith('.gif') || l.endsWith('.svg')) score -= 30
  if (l.includes('.jpg') || l.includes('.jpeg') || l.includes('.png') || l.includes('.webp')) score += 5

  return Math.max(0, Math.min(100, score))
}

/** 用多个搜索词爬取，提高命中率 */
function getSearchQueries(city) {
  return [
    `${encodeURIComponent(city)} 旅游景点 实拍`,
    `${encodeURIComponent(city)} 风景 高清`,
    `${encodeURIComponent(city)} travel landscape`,
  ]
}

/** 爬取单个城市 — 多关键词 + 多图片 + 评分筛选 */
async function scrapeCity(city) {
  const allImages = []
  const queries = getSearchQueries(city)

  for (const query of queries) {
    if (allImages.length >= 12) break  // 收集够了

    const urls = [
      `https://cn.bing.com/images/search?q=${query}&first=1&tsc=ImageBasicHover`,
      `https://www.bing.com/images/search?q=${query}&first=1`,
    ]

    for (const url of urls) {
      try {
        const resp = await axios.get(url, {
          headers: {
            'User-Agent': USER_AGENT,
            'Accept': 'text/html,application/xhtml+xml',
            'Accept-Language': 'zh-CN,zh;q=0.9',
          },
          timeout: 15000,
        })

        const $ = cheerio.load(resp.data)

        // Bing 新版: a.iusc 的 m 属性含 JSON
        $('a.iusc').each((_, el) => {
          try {
            const m = $(el).attr('m')
            if (m) {
              const parsed = JSON.parse(m)
              if (parsed.murl) allImages.push({ url: parsed.murl, title: parsed.t || '' })
            }
          } catch {}
        })

        // 兜底: img.mimg
        $('img.mimg').each((_, el) => {
          const src = $(el).attr('src') || $(el).attr('data-src')
          if (src && src.startsWith('http') && !allImages.find(i => i.url === src)) {
            allImages.push({ url: src, title: $(el).attr('alt') || '' })
          }
        })
      } catch {}
    }
  }

  if (allImages.length === 0) return null

  // 评分 + 排序
  const scored = allImages.map(img => ({
    ...img,
    score: scoreImageUrl(img.url, city),
  })).filter(img => img.score >= 30)  // 30分以下不要
    .sort((a, b) => b.score - a.score)

  return scored.length > 0 ? scored[0].url : null
}

// ==================== 加载已有结果（断点续爬） ====================
const existing = {}
if (fs.existsSync(OUTPUT)) {
  try {
    Object.assign(existing, JSON.parse(fs.readFileSync(OUTPUT, 'utf-8')))
    console.log(`已加载 ${Object.keys(existing).length} 条已有记录，断点续爬`)
  } catch {}
}

// 标记需要重新爬取的（已知低质量URL）
const NEED_RESCUE = new Set()  // 填入已知错误URL的城市名
// 扫描已有记录，标记可疑URL
for (const [city, url] of Object.entries(existing)) {
  if (url && scoreImageUrl(url, city) < 30) {
    NEED_RESCUE.add(city)
    delete existing[city]
  }
}
if (NEED_RESCUE.size > 0) {
  console.log(`检测到 ${NEED_RESCUE.size} 条低质量图片，将重新爬取:\n${[...NEED_RESCUE].join(', ')}\n`)
}

// ==================== 主流程 ====================
async function main() {
  const allCities = [...new Set([...NEED_RESCUE, ...UNIQUE_CITIES])]
  const cities = allCities.filter(c => !existing[c])
  console.log(`共 ${allCities.length} 个城市，已有 ${Object.keys(existing).length} 个，待爬 ${cities.length} 个${NEED_RESCUE.size > 0 ? '（含' + NEED_RESCUE.size + '个重新爬取）' : ''}\n`)

  let count = 0
  let goodCount = 0
  for (const city of cities) {
    process.stdout.write(`[${count + 1}/${cities.length}] ${city} ... `)

    const imgUrl = await scrapeCity(city)
    if (imgUrl && scoreImageUrl(imgUrl, city) >= 30) {
      existing[city] = imgUrl
      goodCount++
      console.log(`✓ (${scoreImageUrl(imgUrl, city)}分)`)
    } else {
      existing[city] = '' // 标记已尝试
      console.log(`✗`)
    }

    count++
    if (count % 10 === 0) {
      fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, 2))
      console.log(`  → 已保存 (本批次合格 ${goodCount}/${count})\n`)
    }

    await new Promise(r => setTimeout(r, DELAY_MS + Math.random() * 500))
  }

  fs.writeFileSync(OUTPUT, JSON.stringify(existing, null, 2))
  const valid = Object.values(existing).filter(Boolean).length
  console.log(`\n====================`)
  console.log(`爬取完成！${Object.keys(existing).length} 个城市，${valid} 个有合格图片`)
  console.log(`输出文件: ${OUTPUT}`)
  console.log(`\n导入数据库: npm run import-images`)
}

main().catch(console.error)
