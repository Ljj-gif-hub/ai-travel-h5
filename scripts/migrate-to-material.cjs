/**
 * 将 city-images.json 数据迁移到 city_material 表
 * 用法: node scripts/migrate-to-material.cjs
 */
const fs = require('fs')
const path = require('path')

const JSON_PATH = path.join(__dirname, '..', 'public', 'city-images.json')
const API = 'http://localhost:3200/api/city/materials/batch'
const BATCH = 50

async function main() {
  if (!fs.existsSync(JSON_PATH)) { console.error('city-images.json 不存在'); return }

  const raw = JSON.parse(fs.readFileSync(JSON_PATH, 'utf-8'))
  const entries = Object.entries(raw).filter(([, url]) => url && url.startsWith('http'))
  console.log(`${Object.keys(raw).length} 条记录, ${entries.length} 条有URL, 开始导入...\n`)

  for (let i = 0; i < entries.length; i += BATCH) {
    const batch = entries.slice(i, i + BATCH).map(([cityName, imageUrl]) => ({ cityName, imageUrl }))
    try {
      const resp = await fetch(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(batch),
      })
      const data = await resp.json()
      const r = data.data || {}
      console.log(`[${i+1}-${Math.min(i+BATCH, entries.length)}/${entries.length}] 新增${r.saved||0} 更新${r.updated||0}`)
    } catch (e) {
      console.error(`批次失败:`, e.message)
    }
    await new Promise(r => setTimeout(r, 200))
  }
  console.log('\n迁移完成！')
}
main().catch(console.error)
