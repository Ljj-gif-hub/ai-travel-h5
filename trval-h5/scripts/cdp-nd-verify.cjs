/* 验证修复后的 note-detail：图片加载 + 底栏几何 */
const EDGE = process.env.EDGE_PATH
const BASE = process.env.BASE_URL || 'http://127.0.0.1:5399'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
async function login() {
  const res = await fetch(`${BASE}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: process.env.ADMIN_PASSWORD }) })
  const json = await res.json()
  if (json.code !== 0) throw new Error('login failed: ' + JSON.stringify(json).slice(0, 150))
  return json.data.token || json.data.accessToken || json.data
}
async function main() {
  const token = await login()
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-ver-${Date.now()}`
  const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-proxy-server', '--remote-debugging-port=9234', `--user-data-dir=${profile}`, '--window-size=414,896', 'about:blank'], { stdio: 'ignore' })
  let targets = null
  for (let i = 0; i < 30; i++) { await sleep(500); try { const res = await fetch('http://127.0.0.1:9234/json'); targets = await res.json(); if (targets.length) break } catch {} }
  const page = targets.find(t => t.type === 'page')
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })
  let msgId = 0; const pending = new Map()
  const events = []
  ws.onmessage = ev => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id) }
    else if (m.method) events.push(m)
  }
  const send = (method, params = {}) => new Promise(resolve => { const id = ++msgId; pending.set(id, resolve); ws.send(JSON.stringify({ id, method, params })) })
  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    return r.result?.result?.value
  }
  await send('Page.enable')
  await send('Network.enable')
  await send('Page.navigate', { url: `${BASE}/favicon.svg` })
  await sleep(1200)
  await evalJs(`sessionStorage.setItem('TOKEN', ${JSON.stringify(token)}); sessionStorage.setItem('CURRENT_USER', 'admin'); 'ok'`)
  await send('Page.navigate', { url: `${BASE}/#/note-detail?id=1` })
  await sleep(7000)

  const reqs = new Map()
  for (const e of events) {
    if (e.method === 'Network.requestWillBeSent') reqs.set(e.params.requestId, { url: e.params.request.url.slice(0, 110), type: e.params.type })
    else if (e.method === 'Network.responseReceived') { const r = reqs.get(e.params.requestId); if (r) r.status = e.params.response.status }
    else if (e.method === 'Network.loadingFailed') { const r = reqs.get(e.params.requestId); if (r) r.failed = e.params.errorText }
  }
  const imgReqs = [...reqs.values()].filter(r => r.type === 'Image')
  console.log('IMG REQUESTS:', JSON.stringify(imgReqs, null, 1))

  const state = await evalJs(`(() => {
    const swipeImgs = [...document.querySelectorAll('.swipe-image')].map(i => ({ cls: i.className, ok: i.complete && i.naturalWidth > 0, srcHead: i.src.slice(0, 60) }))
    const navAv = document.querySelector('.nav-avatar img')
    const cmtAvs = [...document.querySelectorAll('.comment-avatar img')].map(i => ({ ok: i.complete && i.naturalWidth > 0, srcHead: i.src.slice(0, 40) }))
    const bar = document.querySelector('.bottom-bar')
    const barInfo = bar ? { h: bar.offsetHeight, w: bar.offsetWidth, overflow: bar.scrollWidth > bar.offsetWidth } : null
    const items = [...document.querySelectorAll('.bottom-actions .action-item')].map(el => ({ label: el.textContent.trim().slice(0, 8), h: el.offsetHeight, w: el.offsetWidth, labelH: el.querySelector('span') ? el.querySelector('span').offsetHeight : null }))
    const btn = document.querySelector('.comment-oval-btn')
    return JSON.stringify({
      swipeImgs,
      navAvatar: navAv ? { ok: navAv.complete && navAv.naturalWidth > 0, srcHead: navAv.src.slice(0, 40) } : 'no-img',
      cmtAvs,
      barInfo, items,
      writeBtn: btn ? { w: btn.offsetWidth, h: btn.offsetHeight } : null,
      viewportW: window.innerWidth,
    }, null, 1)
  })()`)
  console.log('PAGE STATE:', state)
  ws.close(); edge.kill()
}
main().catch(e => { console.error('FATAL:', e.message); process.exit(1) })
