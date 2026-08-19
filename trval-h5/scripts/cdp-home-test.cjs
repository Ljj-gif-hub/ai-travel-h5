/* CDP test: home page images resolve to local static map, not picsum */
const EDGE = process.env.EDGE_PATH
const PORT = 9223
const HOST = process.env.TEST_HOST || 'http://127.0.0.1:5199'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-home-${Date.now()}`
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
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) {
      pending.get(m.id)(m)
      pending.delete(m.id)
    }
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

  await send('Page.navigate', { url: `${HOST}/#/` })
  await waitFor(`document.readyState === 'complete'`, 15000)
  // wait for static image map to load
  await waitFor(`document.querySelectorAll('.dest-img').length > 0`, 15000)
  await sleep(1500)

  const report = await evalJs(`(() => {
    const imgs = Array.from(document.querySelectorAll('.dest-img')).slice(0, 10).map(i => i.getAttribute('src') || '')
    const banners = Array.from(document.querySelectorAll('.banner-img')).slice(0, 4).map(i => i.getAttribute('src') || '')
    const picsum = imgs.concat(banners).filter(s => s.includes('picsum'))
    return JSON.stringify({ imgs, banners, picsumCount: picsum.length })
  })()`)
  console.log('首页图片报告:', report)

  ws.close()
  edge.kill()
}

main().catch((e) => { console.error('FATAL:', e.message); process.exit(1) })
