// Drives /registrar in headless Chrome at iPhone size: login, record a voice note (fake microphone), undo,
// type a sentence with an unknown category, create it and register, open the edit dialog.
// Run through capture-e2e.sh, which starts Chrome with the fake microphone.
// Env: CDP_PORT, BASE_URL, EMAIL, PASSWORD, OUT (screenshots folder).
import { writeFileSync } from 'node:fs'

const { CDP_PORT = '9334', BASE_URL, EMAIL, PASSWORD, OUT } = process.env
const target = await (await fetch(`http://localhost:${CDP_PORT}/json/new?about:blank`, { method: 'PUT' })).json()
const ws = new WebSocket(target.webSocketDebuggerUrl)
let id = 0
const pending = new Map()
const send = (method, params = {}) =>
  new Promise((resolve) => {
    const i = ++id
    pending.set(i, resolve)
    ws.send(JSON.stringify({ id: i, method, params }))
  })
ws.onmessage = (event) => {
  const message = JSON.parse(event.data)
  if (message.id && pending.has(message.id)) pending.get(message.id)(message.result ?? message.error)
}
await new Promise((resolve) => (ws.onopen = resolve))

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const js = async (expression) =>
  (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.value
const waitFor = async (expression, ms = 30000) => {
  const start = Date.now()
  while (Date.now() - start < ms) {
    if (await js(expression)) return
    await sleep(300)
  }
  throw new Error(`timeout waiting for: ${expression}`)
}
const shot = async (name) => {
  const { data } = await send('Page.captureScreenshot', { format: 'png' })
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(data, 'base64'))
}
const card = () => js(`document.querySelector('[role=status]')?.innerText.replace(/\\n+/g, ' | ') ?? ''`)
const type = async (selector, text) => {
  await js(`document.querySelector('${selector}').focus()`)
  await send('Input.insertText', { text })
}
const clickButton = (label) => js(`[...document.querySelectorAll('button')].find(b => b.innerText.includes(${JSON.stringify(label)}) || b.ariaLabel === ${JSON.stringify(label)}).click()`)

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Network.setExtraHTTPHeaders', { headers: { 'ngrok-skip-browser-warning': '1' } })
await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true })
await send('Page.navigate', { url: `${BASE_URL}/registrar` })
await waitFor(`!!document.querySelector('input[type=email]')`)
await type('input[type=email]', EMAIL)
await type('input[type=password]', PASSWORD)
await js(`document.querySelector('button[type=submit]').click()`)
await waitFor(`document.body.innerText.includes('Fale o gasto')`)
console.log(`1. login → ${await js('location.href')}`)

await clickButton('Gravar')
await waitFor(`document.body.innerText.includes('Gravando')`, 5000)
await sleep(4500)
await clickButton('Parar e enviar')
await waitFor(`!!document.querySelector('[role=status]')`, 45000)
console.log(`2. voz → ${await card()}`)
await shot('1-voz-registrada')

await clickButton('Desfazer')
await waitFor(`document.body.innerText.includes('Desfeito')`)
console.log(`3. desfazer → ${await card()}`)

await type('input[aria-label="Escrever o gasto"]', 'comprei dipirona 12')
await clickButton('Enviar')
await waitFor(`!!document.querySelector('[role=status]') && !document.body.innerText.includes('Registrando')`, 30000)
console.log(`4. texto → ${await card()}`)
await shot('2-texto')
if ((await card()).includes('Criar')) {
  await clickButton('Criar')
  await waitFor(`(document.querySelector('[role=status]')?.innerText ?? '').includes('Desfazer')`, 30000)
  console.log(`5. criar categoria e registrar → ${await card()}`)
}

await clickButton('Editar')
await waitFor(`document.body.innerText.includes('Editar transação')`, 15000)
console.log('6. editar → diálogo aberto')
await shot('3-editar')
ws.close()
