/**
 * 全量重爬 — 百度图片为主 + 标题校验 + 高门槛评分
 * 用法: node scripts/rescrape-all.cjs
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const JSON_PATH = path.join(__dirname, '..', 'public', 'city-images.json')
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'

// ==================== 城市列表 ====================
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
  '日月潭','清境农场','合欢山','阿里山','垦丁','太鲁阁','九份','淡水',
  '东京','大阪','京都','札幌','福冈','名古屋','神户','奈良','冲绳','横滨','箱根','富士山',
  '首尔','釜山','济州','仁川','庆州',
  '曼谷','清迈','普吉岛','芭提雅','苏梅岛','甲米','华欣','清莱','拜县',
  '新加坡','圣淘沙',
  '吉隆坡','槟城','马六甲','兰卡威','沙巴','仙本那',
  '河内','胡志明市','岘港','芽庄','会安','大叻','富国岛','下龙湾','美奈',
  '巴厘岛','雅加达','龙目岛','科莫多岛','民丹岛',
  '马尼拉','长滩岛','宿务','薄荷岛','巴拉望',
  '迪拜','阿布扎比',
  '新德里','孟买','阿格拉','斋浦尔','瓦拉纳西',
  '金边','暹粒','吴哥窟','马尔代夫','马累',
  '科伦坡','康提','加勒','琅勃拉邦','万象','加德满都','博卡拉',
  '伊斯坦布尔','卡帕多奇亚','安塔利亚','棉花堡','耶路撒冷','佩特拉','死海','安曼',
  '巴黎','尼斯','里昂','马赛','波尔多','戛纳','阿维尼翁','科尔马','普罗旺斯','霞慕尼',
  '罗马','威尼斯','米兰','佛罗伦萨','比萨','五渔村','阿马尔菲','科莫湖','多洛米蒂','西西里',
  '伦敦','爱丁堡','曼彻斯特','利物浦','牛津','剑桥','巴斯','约克','格拉斯哥','天空岛','巨石阵',
  '柏林','慕尼黑','法兰克福','汉堡','科隆','海德堡','斯图加特','纽伦堡','新天鹅堡','国王湖','黑森林',
  '巴塞罗那','马德里','塞维利亚','格拉纳达','瓦伦西亚','马拉加','龙达','马略卡岛',
  '苏黎世','日内瓦','卢塞恩','因特拉肯','伯尔尼','采尔马特','少女峰','蒙特勒',
  '雅典','圣托里尼','米克诺斯','克里特','罗德岛','扎金索斯',
  '阿姆斯特丹','鹿特丹','羊角村','风车村','库肯霍夫',
  '布拉格','克鲁姆洛夫','维也纳','萨尔茨堡','哈尔施塔特','因斯布鲁克',
  '雷克雅未克','蓝湖','冰河湖','黄金圈','奥斯陆','卑尔根','特罗姆瑟','罗弗敦群岛',
  '斯德哥尔摩','哥德堡','赫尔辛基','罗瓦涅米','圣诞老人村','哥本哈根',
  '里斯本','波尔图','辛特拉','布达佩斯','杜布罗夫尼克','十六湖',
  '华沙','克拉科夫','都柏林','莫赫悬崖','布鲁塞尔','布鲁日',
  '莫斯科','圣彼得堡','喀山','索契','贝加尔湖','摩尔曼斯克',
  '纽约','洛杉矶','旧金山','拉斯维加斯','芝加哥','华盛顿','波士顿','西雅图','迈阿密','奥兰多','夏威夷',
  '多伦多','温哥华','蒙特利尔','渥太华','卡尔加里','魁北克','班夫','维多利亚','惠斯勒','尼亚加拉瀑布',
  '墨西哥城','坎昆','哈瓦那',
  '里约热内卢','圣保罗','伊瓜苏','布宜诺斯艾利斯','乌斯怀亚','埃尔卡拉法特',
  '库斯科','马丘比丘','乌尤尼盐沼','加拉帕戈斯群岛','复活节岛','百内国家公园',
  '悉尼','墨尔本','黄金海岸','布里斯班','珀斯','凯恩斯','阿德莱德','霍巴特','乌鲁鲁','大堡礁','大洋路',
  '奥克兰','皇后镇','基督城','惠灵顿','罗托鲁瓦','米尔福德峡湾','库克山','霍比屯','蒂卡波湖',
  '楠迪','波拉波拉岛',
  '开罗','卢克索','阿斯旺','阿布辛贝','马拉喀什','卡萨布兰卡','菲斯','舍夫沙万','撒哈拉',
  '开普敦','约翰内斯堡','花园大道','克鲁格国家公园',
  '内罗毕','马赛马拉','桑给巴尔','塞伦盖蒂','乞力马扎罗',
  '路易港','突尼斯城','蓝白小镇','南极半岛','德雷克海峡',
]
const ALL = [...new Set(CITIES)]

// ==================== 严格审核 ====================
const BLOCKED = [
  'icon','logo','avatar','chart','graph','barcode','qr','thumb',
  'product','mall','shop','taobao','jd.','alibaba','aliexpress',
  'mapamundi','politico','nombres','breed','identifier','font.',
  'wallpaper-4k','desktop-wallpaper','discount','coupon','banner',
  'screenshot','diagram','infographic','passport',
  'food/','recipe','game/','meme',
  'box','package','carton','箱子','包装','cat-breed',
  'walmart','amazon.','ebay.','etsy.','shopify',
  'optmv','dress','clothing','shirt','shoe','sneaker',
  'watch','jewelry','perfume','cosmetic','makeup',
  'furniture','appliance','electronic','gadget',
  'hardware','energy','solar','industrial',
  'hanyu-word','stroke-order','calligraphy',
  'dmjnb','chartfarosh','travelfashiongirl',
  'sunsight','.png?','logo-',
]

// 标题关键词校验：标题必须含这些词之一才通过
const TRAVEL_WORDS = ['travel','tour','view','scene','landscape','city','place','sight','trip','photo','pic','image',
  '旅游','旅行','风景','景点','风光','城市','实拍','航拍','夜景','全景','摄影','照片','图片',
  'scenic','tourism','vacation','holiday','destination','attraction','landmark','nature','outdoor']

function scoreImage(url, title) {
  if (!url?.startsWith('http')) return 0
  const l = url.toLowerCase()
  const t = (title || '').toLowerCase()

  // 黑名单 → 0
  for (const b of BLOCKED) if (l.includes(b)) return 0

  // 知名图源 → 从 60 起评，而非直接满分
  let baseScore = 25
  const known = ['zhimg','bcebos','699pic','nipic','tuchong','pixabay','pexels','unsplash','flickr','ctrip','qunar','mafengwo','hdslb','bilibili','douyinpic','gettyimages','shutterstock']
  for (const k of known) if (l.includes(k)) { baseScore = 60; break }

  // 标题校验：含旅游词加分
  let score = baseScore
  let hasTravelWord = false
  for (const tw of TRAVEL_WORDS) {
    if (t.includes(tw) || l.includes(tw)) { hasTravelWord = true; break }
  }
  if (hasTravelWord) score += 15
  else if (baseScore < 60) score -= 10  // 未知域名且无旅游词 → 减分

  if (url.length < 200) score += 5
  if (url.length > 600) score -= 10
  if (l.endsWith('.gif') || l.endsWith('.svg')) score -= 40
  if (l.includes('.jpg') || l.includes('.jpeg') || l.includes('.png') || l.includes('.webp')) score += 5

  return Math.max(0, Math.min(100, score))
}

// 区级地名需要加城市前缀才能搜到正确图片
const CITY_CONTEXT = {
  '东城区':'北京','西城区':'北京','朝阳区':'北京','海淀区':'北京','丰台区':'北京','石景山区':'北京','通州区':'北京','大兴区':'北京','顺义区':'北京','昌平区':'北京','房山区':'北京','门头沟区':'北京','怀柔区':'北京','平谷区':'北京','密云区':'北京','延庆区':'北京',
  '浦东新区':'上海','黄浦区':'上海','徐汇区':'上海','长宁区':'上海','静安区':'上海','虹口区':'上海','杨浦区':'上海','闵行区':'上海','普陀区':'上海','宝山区':'上海','嘉定区':'上海','松江区':'上海','青浦区':'上海','奉贤区':'上海','金山区':'上海','崇明区':'上海',
  '和平区':'天津','河东区':'天津','河西区':'天津','南开区':'天津','河北区':'天津','红桥区':'天津','武清区':'天津','宝坻区':'天津','东丽区':'天津','西青区':'天津','津南区':'天津','北辰区':'天津','静海区':'天津','宁河区':'天津','蓟州区':'天津','滨海新区':'天津',
  '渝中区':'重庆','江北区':'重庆','南岸区':'重庆','沙坪坝区':'重庆','九龙坡区':'重庆','渝北区':'重庆','巴南区':'重庆','北碚区':'重庆','大渡口区':'重庆','涪陵区':'重庆','万州区':'重庆','黔江区':'重庆','长寿区':'重庆','江津区':'重庆','合川区':'重庆','永川区':'重庆',
  '中西区':'香港','湾仔区':'香港','东区':'香港','南区':'香港','油尖旺区':'香港','深水埗区':'香港','九龙城区':'香港','黄大仙区':'香港','观塘区':'香港','荃湾区':'香港','屯门区':'香港','元朗区':'香港','北区':'香港','大埔区':'香港','沙田区':'香港','西贡区':'香港','离岛区':'香港','葵青区':'香港',
  '花地玛堂区':'澳门','圣安多尼堂区':'澳门','大堂区':'澳门','望德堂区':'澳门','风顺堂区':'澳门','嘉模堂区':'澳门','路氹填海区':'澳门','圣方济各堂区':'澳门',
  '中正区':'台北','大同区':'台北','中山区':'台北','松山区':'台北','大安区':'台北','万华区':'台北','信义区':'台北','士林区':'台北','北投区':'台北','内湖区':'台北','南港区':'台北','文山区':'台北',
  '板桥区':'新北','新庄区':'新北','中和区':'新北','永和区':'新北','土城区':'新北','树林区':'新北','三峡区':'新北','莺歌区':'新北','三重区':'新北','芦洲区':'新北','五股区':'新北','金山区':'新北',
  '苓雅区':'高雄','前镇区':'高雄','盐埕区':'高雄','鼓山区':'高雄','旗津区':'高雄','左营区':'高雄','楠梓区':'高雄','三民区':'高雄','新兴区':'高雄','前金区':'高雄','凤山区':'高雄','小港区':'高雄',
  '中区':'台中','西区':'台中','西屯区':'台中','南屯区':'台中','北屯区':'台中','丰原区':'台中','大甲区':'台中','清水区':'台中','雾峰区':'台中',
  '安平区':'台南','安南区':'台南','永康区':'台南','仁德区':'台南',
  '桃园区':'桃园','中坜区':'桃园','平镇区':'桃园','八德区':'桃园','杨梅区':'桃园','芦竹区':'桃园','大溪区':'桃园','龙潭区':'桃园','大园区':'桃园','复兴区':'桃园',
  '花莲市':'花莲','吉安乡':'花莲','寿丰乡':'花莲','凤林镇':'花莲','光复乡':'花莲','瑞穗乡':'花莲','玉里镇':'花莲',
  '台东市':'台东','成功镇':'台东','关山镇':'台东','卑南乡':'台东','池上乡':'台东','鹿野乡':'台东',
  '宜兰市':'宜兰','罗东镇':'宜兰','苏澳镇':'宜兰','头城镇':'宜兰','礁溪乡':'宜兰','壮围乡':'宜兰','冬山乡':'宜兰','三星乡':'宜兰',
  '南投市':'南投','埔里镇':'南投','草屯镇':'南投','竹山镇':'南投','集集镇':'南投',
  '朴子市':'嘉义','布袋镇':'嘉义',
  '屏东市':'屏东','潮州镇':'屏东','东港镇':'屏东','恒春镇':'屏东',
  '苗栗市':'苗栗','头份市':'苗栗','竹南镇':'苗栗','苑里镇':'苗栗','三义乡':'苗栗',
  '竹北市':'新竹','新丰乡':'新竹','湖口乡':'新竹','关西镇':'新竹','内湾':'新竹',
  '彰化市':'彰化','员林市':'彰化','和美镇':'彰化','北斗镇':'彰化','田中镇':'彰化','芳苑乡':'彰化',
  '斗六市':'云林','虎尾镇':'云林','西螺镇':'云林','北港镇':'云林','古坑乡':'云林',
  '马公市':'澎湖','湖西乡':'澎湖','白沙乡':'澎湖','西屿乡':'澎湖','七美':'澎湖','望安':'澎湖','吉贝':'澎湖',
  '金城镇':'金门','金湖镇':'金门','金沙镇':'金门','金宁乡':'金门','烈屿乡':'金门',
  '南竿乡':'马祖','北竿乡':'马祖','莒光乡':'马祖','东引乡':'马祖',
  '暖暖区':'基隆','七堵区':'基隆',
}

function buildQuery(city) {
  const ctx = CITY_CONTEXT[city]
  const name = ctx ? `${ctx}${city}` : city
  return [
    `${encodeURIComponent(name)} 实拍 风景`,
    `${encodeURIComponent(name)} 旅游景点`,
  ]
}

async function scrapeBaidu(city) {
  const all = []
  const queries = buildQuery(city)
  for (const q of queries) {
    if (all.length >= 10) break
    try {
      const resp = await axios.get(`https://image.baidu.com/search?word=${q}&tn=baiduimage&ie=utf-8`, {
        headers: { 'User-Agent': USER_AGENT, 'Accept': 'text/html' },
        timeout: 15000,
      })
      // 百度图片数据在 <script> 中的 objURL
      const html = resp.data
      const matches = html.match(/"objURL"\s*:\s*"(http[^"]+)"/g)
      if (matches) {
        for (const m of matches) {
          const url = m.match(/"(http[^"]+)"/)?.[1]
          if (url) all.push({ url, title: '' })
        }
      }
      // 也尝试 data-imgurl
      const matches2 = html.match(/data-imgurl="(http[^"]+)"/g)
      if (matches2) {
        for (const m of matches2) {
          const url = m.match(/"(http[^"]+)"/)?.[1]
          if (url && !all.find(x => x.url === url)) all.push({ url, title: '' })
        }
      }
    } catch {}
  }
  return all
}

async function scrapeBing(city) {
  const all = []
  const queries = buildQuery(city).concat([
    `${encodeURIComponent(city)} city travel landmark`,
  ])
  for (const q of queries) {
    if (all.length >= 10) break
    for (const base of ['cn.bing.com', 'www.bing.com']) {
      try {
        const resp = await axios.get(`https://${base}/images/search?q=${q}&first=1`, {
          headers: { 'User-Agent': USER_AGENT, 'Accept': 'text/html', 'Accept-Language': 'zh-CN,zh;q=0.9' },
          timeout: 15000,
        })
        const $ = cheerio.load(resp.data)
        $('a.iusc').each((_, el) => {
          try { const m = JSON.parse($(el).attr('m') || '{}'); if (m.murl) all.push({ url: m.murl, title: m.t || '' }) } catch {}
        })
      } catch {}
    }
  }
  return all
}

async function main() {
  const existing = fs.existsSync(JSON_PATH) ? JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8')) : {}
  const need = ALL.filter(c => {
    if (!existing[c]) return true
    return scoreImage(existing[c], '') < 50 // 低于50分重爬
  })

  console.log(`总计 ${ALL.length} 个城市，已有 ${Object.keys(existing).length} 个，需重爬 ${need.length} 个\n`)

  let ok = 0
  for (let i = 0; i < need.length; i++) {
    const city = need[i]
    process.stdout.write(`[${i+1}/${need.length}] ${city} ... `)

    // 百度 + Bing 双源
    const baiduImgs = await scrapeBaidu(city)
    const bingImgs = await scrapeBing(city)
    const allImgs = [...baiduImgs, ...bingImgs]

    const scored = allImgs
      .map(img => ({ ...img, score: scoreImage(img.url, img.title) }))
      .filter(x => x.score >= 50)  // 提高门槛到50
      .sort((a, b) => b.score - a.score)

    if (scored.length > 0) {
      existing[city] = scored[0].url
      ok++
      console.log(`✓ ${scored[0].score}分 (${baiduImgs.length}+${bingImgs.length}候选)`)
    } else {
      existing[city] = ''
      console.log(`✗ (${baiduImgs.length}+${bingImgs.length}候选全不达标)`)
    }

    if ((i+1) % 10 === 0) {
      fs.writeFileSync(JSON_PATH, JSON.stringify(existing, null, 2))
      console.log(`  → 已保存 ${Object.keys(existing).length} 条\n`)
    }
    await new Promise(r => setTimeout(r, 1000 + Math.random() * 500))
  }

  fs.writeFileSync(JSON_PATH, JSON.stringify(existing, null, 2))
  const valid = Object.values(existing).filter(Boolean).length
  console.log(`\n完成！${ALL.length}城市，${valid}有图片，${ALL.length-valid}暂无`)
  console.log('导入数据库: npm run import-images')
}

main().catch(console.error)
