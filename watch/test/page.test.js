import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { NavigationState } from '../lib/protocol.js'

function pageHarness(now = 100) {
  const manifest = JSON.parse(readFileSync(new URL('../app.json', import.meta.url), 'utf8'))
  const widgets = []
  let page
  let client
  let refresh
  let vibrations = 0
  const wakeRelaunch = []
  const context = {
    ui: {
      widget: { TEXT: 'text', IMG: 'image', FILL_RECT: 'background', BUTTON: 'button' },
      prop: { MORE: 'more', VISIBLE: 'visible' }, align: { CENTER_H: 1, CENTER_V: 1 }, text_style: { WRAP: 1 },
      createWidget(type, props) {
        const result = { type, ...props, visible: true, setProperty(property, value) {
          if (property === 'visible') this.visible = value
          else Object.assign(this, value)
        } }
        widgets.push(result)
        return result
      }
    },
    REQUEST_MS: 4000,
    BasePage: definition => ({ ...definition, request: async () => null }),
    getDeviceInfo: () => {
      if (!manifest.permissions.includes('data:os.device.info')) {
        throw new Error('getDeviceInfo requires data:os.device.info')
      }
      return { width: 480, height: 480 }
    },
    setPageBrightTime: () => 0, resetPageBrightTime: () => 0,
    setWakeUpRelaunch: ({ relaunch }) => { wakeRelaunch.push(relaunch) },
    pauseDropWristScreenOff: () => 0, resetDropWristScreenOff: () => 0,
    Vibrator: class { start() { vibrations++ } stop() {} },
    NavigationClient: class {
      constructor(request, changed) {
        this.navigation = new NavigationState()
        this.message = 'Ready'
        this.changed = changed
        client = this
      }
      start() { this.changed() }
      retry() { this.changed() }
      tick() {}
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
  return { page, widgets, get client() { return client }, wakeRelaunch,
    render: () => refresh(), setTime: value => { now = value },
    vibrations: () => vibrations }
}

test('watch startup renders guidance controls before any phone is connected', () => {
  const ui = pageHarness()
  assert.ok(ui.widgets.some(widget => widget.text === 'NAVGO'))
  assert.ok(ui.widgets.some(widget => widget.text === 'Open Zepp + NavGo on phone'))
  assert.ok(ui.widgets.some(widget => widget.type === 'button' && widget.text === 'Retry'))
  ui.page.onDestroy()
})

test('screen-off recovery remains enabled through teardown and rebuild waits for fresh guidance', () => {
  const ui = pageHarness()
  assert.deepEqual(ui.wakeRelaunch, [true])
  const oldClient = ui.client
  oldClient.navigation.update({ state: 1, turn: 2, distance: '200 m', instruction: 'Turn left onto Main Street', summary: '' }, 100)
  ui.render()
  assert.ok(ui.widgets.some(widget => widget.text === '200 m'))
  ui.page.onDestroy()
  assert.equal(ui.wakeRelaunch.at(-1), true, 'Screen-off teardown must not undo OS wake recovery')
  ui.widgets.length = 0
  ui.setTime(30100)
  ui.page.build()
  assert.notEqual(ui.client, oldClient)
  assert.ok(ui.widgets.some(widget => widget.text === 'NOT LIVE / CHECK PHONE'))
  assert.ok(!ui.widgets.some(widget => widget.text === '200 m' || widget.text === 'Turn left onto Main Street'))
  ui.page.onDestroy()
})

test('stale guidance removes the old arrow, distance and street from actual watch widgets', () => {
  const ui = pageHarness()
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
