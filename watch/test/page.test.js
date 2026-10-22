import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { NavigationState } from '../lib/protocol.js'

function pageHarness(now = 100) {
  const widgets = []
  let page
  let client
  let refresh
  let vibrations = 0
  const context = {
    ui: {
      widget: { TEXT: 'text', IMG: 'image', FILL_RECT: 'background', BUTTON: 'button' },
      prop: { MORE: 'more' }, align: { CENTER_H: 1, CENTER_V: 1 }, text_style: { WRAP: 1 },
      createWidget(type, props) {
        const result = { type, ...props, setProperty(property, value) { Object.assign(this, value) } }
        widgets.push(result)
        return result
      }
    },
    ble: {},
    getDeviceInfo: () => ({ width: 480, height: 480 }),
    setPageBrightTime: () => 0, resetPageBrightTime: () => 0,
    pauseDropWristScreenOff: () => 0, resetDropWristScreenOff: () => 0,
    Vibrator: class { start() { vibrations++ } stop() {} },
    NavigationClient: class {
      constructor(ble, changed) {
        this.navigation = new NavigationState()
        this.phones = []
        this.selected = null
        this.message = 'Ready'
        this.changed = changed
        client = this
      }
      start() { this.changed() }
      close() {}
    },
    Page: definition => { page = definition },
    Date: class extends Date { static now() { return now } },
    setInterval: callback => { refresh = callback; return 1 },
    clearInterval: () => {}
  }
  const source = readFileSync(new URL('../page/index.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '')
  vm.runInNewContext(source, context)
  page.build()
  return { page, widgets, client, render: () => refresh(), setTime: value => { now = value },
    vibrations: () => vibrations }
}

test('stale guidance removes the old arrow, distance and street from actual watch widgets', () => {
  const ui = pageHarness()
  ui.client.selected = { code: 'ABCDEF' }
  ui.client.navigation.update({ state: 1, turn: 2, distance: '200 m', instruction: 'Turn left onto Main Street', summary: '' }, 100)
  ui.render()
  assert.equal(ui.widgets.find(widget => widget.type === 'image').src, 'turn-2.png')
  assert.ok(ui.widgets.some(widget => widget.text === '200 m'))
  assert.equal(ui.vibrations(), 1)
  ui.render()
  assert.equal(ui.vibrations(), 1, 'Heartbeat must not repeatedly vibrate')
  ui.setTime(15100)
  ui.render()
  assert.equal(ui.widgets.find(widget => widget.type === 'image').src, 'turn-0.png')
  assert.ok(ui.widgets.some(widget => widget.text === 'NOT LIVE / CHECK PHONE'))
  assert.ok(!ui.widgets.some(widget => widget.text === '200 m' || widget.text === 'Turn left onto Main Street'))
  ui.page.onDestroy()
})

test('all watch action button corners fit inside the round screen', () => {
  const ui = pageHarness()
  for (const widget of ui.widgets.filter(widget => widget.type === 'button')) {
    for (const x of [widget.x, widget.x + widget.w]) {
      for (const y of [widget.y, widget.y + widget.h]) {
        assert.ok(Math.hypot(x - 240, y - 240) < 240, widget.text + ' clips the bezel')
      }
    }
  }
  ui.page.onDestroy()
})
