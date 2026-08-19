/* 验证 TripsView 推荐景点卡片图片 */
const EDGE = process.env.EDGE_PATH
const BASE = process.env.BASE_URL || 'http://127.0.0.1:5399'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
async function login() {
  const res = await fetch(`${BASE}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: process.env.ADMIN_PASSWORD }) })
  const json = await res.json()
  if (json.code !== 0) throw new Error('login failed')
  return json.data.token || json.data.accessToken || json.data
}
async function main() {
  const token = await login()
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-trips-${Date.now()}`
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-proxy-server', '--remote-debugging-port=9235', `--user-data-dir=${profile}`, '--window-size=414,896', 'about:blank'], { stdio: 'ignore' })
  let targets = null
  for (let i = 0; i < 30; i++) { await sleep(500); try { const res = await fetch('http://127.0.0.1:9235/json'); targets = await res.json(); if (targets.length) break } catch {} }
  const page = targets.find(t => t.type === 'page')
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })
  let msgId = 0; const pending = new Map()
  ws.onmessage = ev => { const m = JSON.parse(ev.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) } }
  const send = (method, params = {}) => new Promise(resolve => { const id = ++msgId; pending.set(id, resolve); ws.send(JSON.stringify({ id, method, params })) })
  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    return r.result?.result?.value
  }
  await send('Page.enable')
  await send('Page.navigate', { url: `${BASE}/favicon.svg` })
  await sleep(1200)
  await evalJs(`sessionStorage.setItem('TOKEN', ${JSON.stringify(token)}); sessionStorage.setItem('CURRENT_USER', 'admin'); 'ok'`)
  await send('Page.navigate', { url: `${BASE}/#/trips` })
  await sleep(3000)
  // 滚到底部触发懒加载
  for (let i = 0; i < 8; i++) {
    await evalJs(`document.querySelector('.trips-scroll') ? document.querySelector('.trips-scroll').scrollTop = document.querySelector('.trips-scroll').scrollHeight : window.scrollTo(0, document.body.scrollHeight); 'ok'`)
    await sleep(1500)
  }
  const state = await evalJs(`(() => {
    const cards = [...document.querySelectorAll('.guide-card')]
    const imgs = [...document.querySelectorAll('.guide-card-img')].map(i => ({
      src: i.src.slice(0, 90),
      ok: i.complete && i.naturalWidth > 0,
      hidden: i.style.display === 'none',
    }))
    const sections = [...document.querySelectorAll('.guide-section .sec-title')].map(t => t.textContent.trim())
    return JSON.stringify({ sections, cardCount: cards.length, imgs }, null, 1)
  })()`)
  console.log(state)
  ws.close(); edge.kill()
}
main().catch(e => { console.error('FATAL:', e.message); process.exit(1) })
