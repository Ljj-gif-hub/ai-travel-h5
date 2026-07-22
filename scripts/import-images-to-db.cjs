/**
 * 将 city-images.json 批量导入后端数据库 city_material 表
 * 用法：node scripts/import-images-to-db.cjs
 * 前提：后端 SpringBoot 已启动（localhost:3200）
 */
const fs = require('fs')
const path = require('path')

const JSON_PATH = path.join(__dirname, '..', 'public', 'city-images.json')
const API = 'http://localhost:3200/api/city/materials/batch'
const BATCH = 50

async function main() {
  if (!fs.existsSync(JSON_PATH)) {
    console.error('city-images.json 不存在，请先运行 npm run scrape-images')
    process.exit(1)
  }

  const raw = JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8'))
  const entries = Object.entries(raw).filter(([, url]) => url)
  console.log(`共 ${Object.keys(raw).length} 条记录，${entries.length} 条有图片URL\n`)

  for (let i = 0; i < entries.length; i += BATCH) {
    const batch = entries.slice(i, i + BATCH).map(([cityName, imageUrl]) => ({
      cityName,
      imageUrl,
    }))

    try {
      const resp = await fetch(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(batch),
      })
      const data = await resp.json()
      console.log(`[${i + 1}-${Math.min(i + BATCH, entries.length)}/${entries.length}] 新增${data.data?.saved || 0} 更新${data.data?.updated || 0}`)
    } catch (e) {
      console.error(`批次 ${i} 导入失败:`, e.message)
    }

    await new Promise(r => setTimeout(r, 200))
  }

  console.log('\n导入完成！')
}

main().catch(console.error)
