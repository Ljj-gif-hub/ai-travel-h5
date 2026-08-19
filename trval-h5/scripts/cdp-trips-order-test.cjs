/* CDP test: TripsView trips-list-section is above guide-zone (行程规划置顶) */
const EDGE = process.env.EDGE_PATH
const PORT = 9224
const HOST = process.env.TEST_HOST || 'http://127.0.0.1:5199'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function b64url(obj) {
  return Buffer.from(JSON.stringify(obj)).toString('base64url')
}

async function main() {
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-trips-${Date.now()}`
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-proxy-server',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profile}`,
    '--window-size=414,896',
    'about:blank',
  ], { stdio: 'ignore' })

  let targets = null
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json`)
      targets = await res.json()
      if (targets.length) break
    } catch {}
  }
  if (!targets) throw new Error('CDP not ready')
  const page = targets.find((t) => t.type === 'page')
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })

  let msgId = 0
  const pending = new Map()
  const logs = []
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return }
    if (m.method === 'Runtime.exceptionThrown') logs.push('[EXC] ' + (m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text))
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') logs.push('[ERR] ' + m.params.entry.text.slice(0, 150))
  }
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++msgId
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })
  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
    if (r.result?.exceptionDetails) return { error: r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text }
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 20000) => {
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      const v = await evalJs(expr)
      if (v) return true
      await sleep(500)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Log.enable')

  await send('Page.navigate', { url: `${HOST}/#/login` })
  await waitFor(`location.origin === ${JSON.stringify(new URL(HOST).origin)} && document.readyState === 'complete'`, 15000)
  await sleep(2000)
  const exp = Math.floor(Date.now() / 1000) + 86400
  const fakeJwt = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ sub: 'test', exp })}.fakesig`
  await evalJs(`sessionStorage.setItem('TOKEN', ${JSON.stringify(fakeJwt)}); sessionStorage.setItem('REFRESH_TOKEN', 'fake-refresh'); 'ok'`)

  await send('Page.navigate', { url: `${HOST}/#/trips` })
  const tripsReady = await waitFor(`!!document.querySelector('.trips-inner')`, 20000)
  console.log('trips page ready:', tripsReady)
  await sleep(2500)

  const report = await evalJs(`(() => {
    const inner = document.querySelector('.trips-inner')
    if (!inner) return JSON.stringify({ error: 'no .trips-inner' })
    const children = Array.from(inner.children)
    const order = children.map((el, i) => {
      const cls = el.className && typeof el.className === 'string' ? el.className.slice(0, 40) : String(el.tagName).toLowerCase()
      const heading = (el.textContent || '').trim().slice(0, 12)
      return i + ':' + cls + '[' + heading + ']'
    })
    const tripsIdx = children.findIndex(c => (c.className || '').includes('trips-list') || (c.textContent || '').includes('行程规划'))
    const guideIdx = children.findIndex(c => (c.className || '').includes('guide') || (c.textContent || '').includes('景点推荐') || (c.textContent || '').includes('游玩攻略'))
    return JSON.stringify({ order, tripsIdx, guideIdx, tripsBeforeGuide: tripsIdx !== -1 && (guideIdx === -1 || tripsIdx < guideIdx) })
  })()`)
  console.log('TripsView 结构:', report)
  console.log('--- page logs ---')
  logs.slice(0, 10).forEach((l) => console.log(l))

  ws.close()
  edge.kill()
}

main().catch((e) => { console.error('FATAL:', e.message); process.exit(1) })
