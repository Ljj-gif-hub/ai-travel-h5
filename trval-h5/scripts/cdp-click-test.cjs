/* CDP click test: agent-planner origin row navigates to /city-select */
const EDGE = process.env.EDGE_PATH
const PORT = 9222
const HOST = process.env.TEST_HOST || 'http://127.0.0.1:5199'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function b64url(obj) {
  return Buffer.from(JSON.stringify(obj)).toString('base64url')
}

async function main() {
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-profile-${Date.now()}`
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
  console.log('CDP target:', page.url)

  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })

  let msgId = 0
  const pending = new Map()
  const logs = []
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) {
      pending.get(m.id)(m)
      pending.delete(m.id)
      return
    }
    if (m.method === 'Runtime.exceptionThrown') {
      logs.push('[EXC] ' + (m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text))
    }
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      logs.push('[ERR] ' + m.params.entry.text.slice(0, 200))
    }
  }
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++msgId
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })
  const evalJs = async (expr, awaitPromise = false) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise })
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

  // 1. open login page on same origin, inject non-expired fake token
  await send('Page.navigate', { url: `${HOST}/#/login` })
  await waitFor(`location.origin === ${JSON.stringify(new URL(HOST).origin)} && document.readyState === 'complete'`, 15000)
  await sleep(2000)
  const exp = Math.floor(Date.now() / 1000) + 86400
  const fakeJwt = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ sub: 'test', exp })}.fakesig`
  console.log('inject token:', await evalJs(`sessionStorage.setItem('TOKEN', ${JSON.stringify(fakeJwt)}); sessionStorage.setItem('REFRESH_TOKEN', 'fake-refresh'); 'ok'`))

  // 2. open agent-planner
  await send('Page.navigate', { url: `${HOST}/#/agent-planner` })
  const plannerReady = await waitFor(`!!document.querySelector('.planner-page')`, 15000)
  console.log('planner ready:', plannerReady)
  console.log('hash:', await evalJs('location.hash'))
  console.log('form-row count:', await evalJs(`document.querySelectorAll('.form-row.clickable').length`))
  console.log('row texts:', await evalJs(`Array.from(document.querySelectorAll('.form-row.clickable')).map(r => r.textContent.trim().slice(0,20)).join(' | ')`))

  // 3. click first form-row (origin)
  const clickResult = await evalJs(`(() => {
    const row = document.querySelectorAll('.form-row.clickable')[0]
    if (!row) return 'row-not-found'
    row.click()
    return 'clicked'
  })()`)
  console.log('click:', clickResult)

  await sleep(2500)
  console.log('after click hash:', await evalJs('location.hash'))
  console.log('city-select rendered:', await evalJs(`!!document.querySelector('.city-select-page')`))
  console.log('--- page logs ---')
  logs.slice(0, 20).forEach((l) => console.log(l))

  ws.close()
  edge.kill()
}

main().catch((e) => { console.error('FATAL:', e.message); process.exit(1) })
