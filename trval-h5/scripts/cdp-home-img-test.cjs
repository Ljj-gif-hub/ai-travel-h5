/* 检查首页图片:img src 是否为本地 /images/ 路径而非 picsum */
const EDGE = process.env.EDGE_PATH
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-home-${Date.now()}`
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-proxy-server',
    '--remote-debugging-port=9224',
    `--user-data-dir=${profile}`,
    '--window-size=414,896',
    'about:blank',
  ], { stdio: 'ignore' })

  let targets = null
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    try {
      const res = await fetch('http://127.0.0.1:9224/json')
      targets = await res.json()
      if (targets.length) break
    } catch {}
  }
  const page = targets.find((t) => t.type === 'page')
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })
  let msgId = 0
  const pending = new Map()
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
  }
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++msgId
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })
  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true })
    return r.result?.result?.value
  }

  await send('Page.enable')
  const base = process.env.BASE_URL || 'http://127.0.0.1:5299'
  await send('Page.navigate', { url: `${base}/#/` })
  await sleep(2000)
  for (let i = 0; i < 20; i++) {
    const ready = await evalJs(`document.querySelectorAll('.dest-img, .banner-img').length > 0`)
    if (ready) break
    await sleep(1000)
  }
  await sleep(2000)

  const report = await evalJs(`(() => {
    const srcs = [...document.querySelectorAll('.dest-img, .banner-img, .event-img, .city-img')]
      .map(img => img.src)
      .filter(Boolean)
    const local = srcs.filter(s => s.includes('/images/'))
    const picsum = srcs.filter(s => s.includes('picsum'))
    const api = srcs.filter(s => s.includes('/api/city/image'))
    const other = srcs.filter(s => !s.includes('/images/') && !s.includes('picsum') && !s.includes('/api/'))
    return JSON.stringify({ total: srcs.length, local: local.length, picsum: picsum.length, api: api.length, other: other.length, picsumList: picsum.slice(0,5), sample: srcs.slice(0,6) })
  })()`)
  console.log('首页图片统计:', report)

  ws.close()
  edge.kill()
}

main().catch((e) => { console.error('FATAL:', e.message); process.exit(1) })
