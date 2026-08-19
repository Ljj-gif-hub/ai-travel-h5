/* 诊断 CDP Edge 网络连通性 */
const { spawn } = require('child_process')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const edge = process.env.EDGE_PATH
  const p = spawn(edge, [
    '--headless=new', '--disable-gpu', '--no-proxy-server',
    '--remote-debugging-port=9223',
    `--user-data-dir=${process.env.TEMP}\\cdp-diag-${Date.now()}`,
    'about:blank',
  ], { stdio: 'ignore' })

  let t = null
  for (let i = 0; i < 20; i++) {
    await sleep(500)
    try {
      const r = await fetch('http://127.0.0.1:9223/json')
      t = await r.json()
      if (t.length) break
    } catch {}
  }
  const page = t.find((x) => x.type === 'page')
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r) => (ws.onopen = r))
  let id = 0
  const send = (m, prm) => new Promise((res) => {
    const i = ++id
    const h = (e) => {
      const d = JSON.parse(e.data)
      if (d.id === i) { ws.removeEventListener('message', h); res(d) }
    }
    ws.addEventListener('message', h)
    ws.send(JSON.stringify({ id: i, method: m, params: prm || {} }))
  })

  await send('Page.enable')
  await send('Page.navigate', { url: 'https://example.com' })
  await sleep(3000)
  let r = await send('Runtime.evaluate', { expression: 'location.href', returnByValue: true })
  console.log('example.com =>', r.result.result.value)

  await send('Page.navigate', { url: 'http://127.0.0.1:5199/' })
  await sleep(4000)
  r = await send('Runtime.evaluate', { expression: 'location.href', returnByValue: true })
  console.log('vite href =>', r.result.result.value)
  r = await send('Runtime.evaluate', { expression: 'document.body ? document.body.innerText.slice(0, 150) : "nobody"', returnByValue: true })
  console.log('vite body =>', r.result.result.value)

  ws.close()
  p.kill()
}

main().catch((e) => { console.log('ERR', e.message); process.exit(1) })
