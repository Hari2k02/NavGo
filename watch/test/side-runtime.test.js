import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { PhoneBridge } from '../lib/bridge.js'

test('side service rejects ZML generic HTTP even after plugin installation', async () => {
  let service
  let fetches = 0
  const source = readFileSync(new URL('../app-side/index.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '')
  vm.runInNewContext(source, {
    PhoneBridge,
    settings: { settingsStorage: { getItem: () => 'ab'.repeat(32) } },
    BaseSideService: options => Object.assign(options, {
      fetch: async () => { fetches++; return {} },
      httpRequestHandler: () => { throw new Error('Generic proxy must not be reachable') }
    }),
    AppSideService: options => { service = options }
  })
  service.onInit()
  service.httpRequestHandler({ params: { url: 'https://example.invalid' } }, (error, body) => {
    assert.equal(error.code, 'UNSUPPORTED')
    assert.equal(body, null)
  })
  assert.equal(fetches, 0)
  const result = await new Promise(resolve =>
    service.onRequest({ method: 'navgo.snapshot', params: { requestId: 'x' } }, (error, body) => resolve(body)))
  assert.equal(result.error, 'INVALID_RESPONSE')
  assert.equal(fetches, 1)
  service.onDestroy()
})

test('settings separate the key control from text and save without redisplaying the key', () => {
  const stored = new Map()
  let settings
  const source = readFileSync(new URL('../app-settings/index.js', import.meta.url), 'utf8')
  vm.runInNewContext(source, {
    AppSettingsPage: options => { settings = options },
    View: (props, children) => ({ ...props, children }),
    Text: (props, text) => ({ text }),
    TextInput: props => ({ input: true, ...props }),
    Button: props => ({ button: true, ...props })
  })
  const props = { settingsStorage: {
    getItem: key => stored.get(key) ?? null,
    setItem: (key, value) => stored.set(key, value),
    removeItem: key => stored.delete(key)
  } }
  function controls() {
    const page = settings.build(props)
    assert.equal(page.children.length, 6)
    for (const row of page.children) {
      assert.equal(row.style.display, 'block', 'Each control must occupy its own row, not run into adjacent text')
      assert.equal(row.children.length, 1)
    }
    const nodes = page.children.map(row => row.children[0])
    const input = nodes.find(node => node.input)
    assert.equal(input.labelStyle.display, 'block')
    assert.ok(parseInt(input.labelStyle.padding) >= 12, 'The editor opener must have a visible tap target')
    return nodes
  }
  let nodes = controls()
  nodes.find(node => node.input).onChange(' ' + 'AB'.repeat(32) + ' ')
  assert.equal(stored.get('setupKey'), 'ab'.repeat(32))
  nodes = controls()
  assert.equal(nodes.find(node => node.input).value, '')
  assert.ok(!JSON.stringify(nodes).includes('ab'.repeat(32)))
  nodes.find(node => node.button).onClick()
  assert.equal(stored.has('setupKey'), false)
  nodes.find(node => node.input).onChange('invalid')
  assert.equal(stored.has('setupKey'), false)
  assert.ok(stored.get('setupError'))
})
