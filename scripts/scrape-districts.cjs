/**
 * 补爬区级地名 — 用城市前缀避免歧义
 */
const axios = require('axios')
const cheerio = require('cheerio')
const fs = require('fs')
const path = require('path')

const OUT = path.join(__dirname, '..', 'public', 'city-images.json')
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36'

// 区名 → 城市前缀
const DISTRICTS = {
  '北京': ['东城区','西城区','朝阳区','海淀区','丰台区','石景山区','通州区','大兴区','顺义区','昌平区','房山区','门头沟区','怀柔区','平谷区','密云区','延庆区'],
  '上海': ['浦东新区','黄浦区','徐汇区','长宁区','静安区','虹口区','杨浦区','闵行区','普陀区','宝山区','嘉定区','松江区','青浦区','奉贤区','金山区','崇明区'],
  '天津': ['和平区','河东区','河西区','南开区','河北区','红桥区','武清区','宝坻区','东丽区','西青区','津南区','北辰区','静海区','宁河区','蓟州区','滨海新区'],
  '重庆': ['渝中区','江北区','南岸区','沙坪坝区','九龙坡区','渝北区','巴南区','北碚区','大渡口区','涪陵区','万州区','黔江区','长寿区','江津区','合川区','永川区'],
  '香港': ['中西区','湾仔区','东区','南区','油尖旺区','深水埗区','九龙城区','黄大仙区','观塘区','荃湾区','屯门区','元朗区','北区','大埔区','沙田区','西贡区','离岛区','葵青区'],
  '澳门': ['花地玛堂区','圣安多尼堂区','大堂区','望德堂区','风顺堂区','嘉模堂区','路氹填海区','圣方济各堂区'],
}

const BLOCK = ['icon','logo','avatar','chart','graph','barcode','qr','thumb','product','mall','shop','taobao','jd.','alibaba','walmart','amazon.','dress','clothing','shoe','jewelry','furniture','font.','breed','identifier','wallpaper-4k','discount','coupon','banner','screenshot','diagram','game','meme','box','package','carton']
const PREFER = ['wikipedia','wikimedia','flickr','unsplash','pexels','pixabay','zhimg','bcebos','699pic','ctrip','qunar','mafengwo','gettyimages','shutterstock','baidu.com']

function score(u) {
  const l = u.toLowerCase()
  for (const b of BLOCK) if (l.includes(b)) return 0
  let s = 30
  for (const p of PREFER) if (l.includes(p)) { s = 70; break }
  if (u.length < 150) s += 15
  if (u.length > 500) s -= 10
  if (l.endsWith('.gif')||l.endsWith('.svg')) s -= 40
  return Math.max(0,Math.min(100,s))
}

async function scrape(city, prefix) {
  const q = encodeURIComponent(`${prefix}${city} 实拍`)
  const imgs = []
  for (const host of ['cn.bing.com','www.bing.com']) {
    try {
      const r = await axios.get(`https://${host}/images/search?q=${q}&first=1`,{
        headers:{'User-Agent':UA,'Accept':'text/html','Accept-Language':'zh-CN'},timeout:10000
      })
      const $ = cheerio.load(r.data)
      $('a.iusc').each((_,el)=>{
        try{const m=JSON.parse($(el).attr('m')||'{}');if(m.murl)imgs.push(m.murl)}catch{}
      })
    }catch{}
  }
  return imgs.map(u=>({url:u,s:score(u)})).filter(x=>x.s>=50).sort((a,b)=>b.s-a.s)
}

async function main(){
  const all = JSON.parse(fs.readFileSync(OUT,'utf-8'))
  let total=0,ok=0
  for (const [city, districts] of Object.entries(DISTRICTS)) {
    for (const d of districts) {
      if (all[d] && all[d].startsWith('http')) continue
      total++
      process.stdout.write(`${city}${d} `)
      const results = await scrape(d, city)
      if (results.length>0) { all[d]=results[0].url; ok++; console.log(`✓ ${results[0].s}分`) }
      else { console.log('✗') }
      await new Promise(r=>setTimeout(r,800))
    }
  }
  fs.writeFileSync(OUT, JSON.stringify(all,null,2))
  console.log(`\n补爬完成: ${total}个区, 成功${ok}`)
}
main().catch(console.error)
