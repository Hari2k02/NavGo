import * as ui from '@zos/ui'
import { BasePage } from '@zeppos/zml/base-page'
import { getDeviceInfo } from '@zos/device'
import { setWakeUpRelaunch, setPageBrightTime, resetPageBrightTime, pauseDropWristScreenOff, resetDropWristScreenOff } from '@zos/display'
import { Vibrator } from '@zos/sensor'
import { NavigationClient, REQUEST_MS } from '../lib/client.js'

/** @type {NavigationClient | null} */
let client = null
/** @type {ReturnType<typeof setInterval> | null} */
let tick = null
/** @type {(() => void) | null} */
let render = null
let awake = false
let awakeUntil = 0
let lastTurn = ''
const vibrator = new Vibrator()

Page(BasePage({
  build() {
    // Restore this page after screen-off; this does not block system notifications.
    setWakeUpRelaunch({ relaunch: true })
    const { width, height } = getDeviceInfo()
    const scale = width / 480
    /** @param {number} value */
    const p = value => Math.round(value * scale)
    /** @param {number} y @param {number} h @param {number} size @param {number} color */
    const text = (y, h, size, color) => ui.createWidget(ui.widget.TEXT, {
      x: p(48), y: p(y), w: width - p(96), h: p(h), color,
      text_size: p(size), align_h: ui.align.CENTER_H, align_v: ui.align.CENTER_V,
      text_style: ui.text_style.WRAP, text: ''
    })
    ui.createWidget(ui.widget.FILL_RECT, { x: 0, y: 0, w: width, h: height, color: 0x101612, radius: 0, angle: 0 })
    const brand = text(25, 30, 18, 0xc9fa73)
    brand.setProperty(ui.prop.MORE, { text: 'NAVGO' })
    const status = text(58, 42, 16, 0xb8c7bb)
    const arrow = ui.createWidget(ui.widget.IMG, {
      x: p(192), y: p(108), w: p(96), h: p(96), src: 'turn-0.png', auto_scale: true
    })
    const distance = text(204, 65, 49, 0xc9fa73)
    const instruction = text(270, 80, 23, 0xffffff)
    const summary = text(350, 30, 16, 0xb8c7bb)
    const next = ui.createWidget(ui.widget.BUTTON, {
      x: p(100), y: p(390), w: p(132), h: p(42), radius: p(21),
      normal_color: 0x243127, press_color: 0x3c5141, text: 'Screen: auto',
      text_size: p(15), color: 0xffffff,
      click_func: () => {
        if (!client) return
        awake = !awake
        if (awake) {
          if (setPageBrightTime({ brightTime: 3600000 }) !== 0
            || pauseDropWristScreenOff({ duration: 3600000 }) !== 0) {
            awake = false
            resetPageBrightTime()
            resetDropWristScreenOff()
            client.message = 'Screen hold not supported'
          } else {
            awakeUntil = Date.now() + 3600000
          }
        } else {
          resetPageBrightTime()
          resetDropWristScreenOff()
        }
        if (render) render()
      }
    })
    ui.createWidget(ui.widget.BUTTON, {
      x: p(248), y: p(390), w: p(132), h: p(42), radius: p(21),
      normal_color: 0xc9fa73, press_color: 0x98c54b, text: 'Retry',
      text_size: p(17), color: 0x101612,
      click_func: () => {
        if (!client) return
        client.retry()
      }
    })
    render = () => {
      if (!client) return
      const now = Date.now()
      if (awake && now >= awakeUntil) {
        awake = false
        resetPageBrightTime()
        resetDropWristScreenOff()
      }
      const nav = client.navigation.current(now)
      next.setProperty(ui.prop.MORE, { text: awake ? 'Screen: ON 1h' : 'Screen: auto' })
      if (!nav || nav.state === 3) {
        status.setProperty(ui.prop.MORE, { text: 'NOT LIVE / CHECK PHONE' })
        arrow.setProperty(ui.prop.MORE, { src: 'turn-0.png' })
        distance.setProperty(ui.prop.MORE, { text: '--' })
        instruction.setProperty(ui.prop.MORE, { text: nav ? nav.instruction : 'Open Zepp + NavGo on phone' })
        summary.setProperty(ui.prop.MORE, { text: client.message })
        return
      }
      status.setProperty(ui.prop.MORE, { text: nav.state === 1 ? 'LIVE / GOOGLE MAPS' : nav.state === 2 ? 'ARRIVED' : 'READY / NO ACTIVE ROUTE' })
      arrow.setProperty(ui.prop.MORE, { src: 'turn-' + (nav.state === 1 ? nav.turn : 0) + '.png' })
      distance.setProperty(ui.prop.MORE, { text: nav.state === 1 ? nav.distance || '--' : nav.state === 2 ? 'Done' : '--' })
      instruction.setProperty(ui.prop.MORE, { text: nav.instruction })
      summary.setProperty(ui.prop.MORE, { text: nav.summary })
      const key = nav.state + ':' + nav.turn + ':' + nav.instruction
      if (key !== lastTurn && (nav.state === 1 || nav.state === 2)) vibrator.start()
      lastTurn = key
    }
    // ZML supports request options at runtime; its published declarations omit them.
    const request = /** @type {(data: {method: string, params: {requestId: string}}, options: {timeout: number}) => Promise<unknown>} */ (this.request.bind(this))
    client = new NavigationClient(id => request({ method: 'navgo.snapshot', params: { requestId: id } },
      { timeout: REQUEST_MS }), render)
    render()
    client.start()
    tick = setInterval(() => { if (client) client.tick(); if (render) render() }, 1000)
  },
  onDestroy() {
    // Do not cancel wake relaunch here: screen-off can also destroy the page.
    if (tick !== null) clearInterval(tick)
    tick = null
    if (client) client.close()
    client = null
    render = null
    vibrator.stop()
    resetPageBrightTime()
    resetDropWristScreenOff()
    awake = false
    lastTurn = ''
  }
}))
