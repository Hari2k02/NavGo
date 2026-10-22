import { DATA_UUID, SERVICE_UUID, FrameAssembler, NavigationState } from './protocol.js'

/** @typedef {Pick<typeof import('@zos/ble'),
 * 'mstStartScan' | 'mstStopScan' | 'mstConnect' | 'mstDisconnect' | 'mstBuildProfile' |
 * 'mstGetProfileInstance' | 'mstPrepare' | 'mstOnPrepare' | 'mstOnCharaValueArrived' |
 * 'mstOnCharaReadComplete' | 'mstReadCharacteristic' | 'mstDestroyProfileInstance' | 'mstOffAllCb'>} BleApi */
/** @typedef {{address: ArrayBuffer, code: string, key: string}} Phone */

/** @param {string} uuid */
function canonical(uuid) {
  return uuid.replace(/-/g, '').toLowerCase()
}

/** @param {ArrayBuffer} buffer */
function hex(buffer) {
  return Array.from(new Uint8Array(buffer), value => value.toString(16).padStart(2, '0')).join('').toUpperCase()
}

export class NavigationClient {
  /** @param {BleApi} ble @param {() => void} changed @param {() => number} now */
  constructor(ble, changed, now = () => Date.now()) {
    this.ble = ble
    this.changed = changed
    this.now = now
    this.navigation = new NavigationState()
    this.assembler = new FrameAssembler()
    /** @type {Phone[]} */
    this.phones = []
    /** @type {Phone | null} */
    this.selected = null
    /** @type {number | null} */
    this.connection = null
    /** @type {number | null} */
    this.profile = null
    /** @type {ReturnType<typeof setTimeout> | null} */
    this.timer = null
    this.reading = false
    this.scanning = false
    this.closed = false
    this.generation = 0
    this.retry = 0
    this.connectedAt = 0
    this.message = 'Start sharing on your phone'
    this.started = false
  }

  start() {
    if (this.started) return
    this.started = true
    const prepared = this.ble.mstOnPrepare((profile, status) => {
      if (this.closed || this.connection === null || (this.profile !== null && profile !== this.profile)) return
      if (status !== 0) {
        this.failed('Pairing or service setup failed')
        return
      }
      this.profile = profile
      this.message = 'Pair and approve this watch on phone'
      this.changed()
      this.later(() => this.read(), 100)
    })
    const values = this.ble.mstOnCharaValueArrived((profile, uuid, data, status) => {
      if (this.closed || profile !== this.profile || canonical(uuid) !== canonical(DATA_UUID) || !this.reading) return
      this.reading = false
      if (status !== 0) {
        this.readError(status)
        return
      }
      try {
        const nav = this.assembler.push(data, this.now())
        if (nav !== null) {
          this.navigation.update(nav, this.now())
          this.message = 'Live navigation'
          this.retry = 0
          this.changed()
        }
        this.later(() => this.read(), nav === null ? 80 : 1000)
      } catch (error) {
        this.failed('Invalid navigation data. Reconnecting.')
      }
    })
    const complete = this.ble.mstOnCharaReadComplete((profile, uuid, status) => {
      if (this.closed || profile !== this.profile || canonical(uuid) !== canonical(DATA_UUID)) return
      if (status !== 0 && this.reading) {
        this.reading = false
        this.readError(status)
      }
    })
    if (!prepared || !values || !complete) {
      this.message = 'BLE APIs unavailable on this firmware'
      this.changed()
      return
    }
    this.scan()
  }

  scan() {
    if (this.closed) return
    this.generation++
    this.cancelTimer()
    this.disconnect()
    this.selected = null
    this.phones = []
    this.scanning = true
    this.message = 'Scanning for NavGo phones...'
    const generation = this.generation
    const started = this.ble.mstStartScan(result => {
      if (this.closed || !this.scanning || generation !== this.generation) return
      if (result.dev_addr.byteLength !== 6) return
      for (const service of result.service_data_array || []) {
        if (canonical(service.uuid) !== canonical(SERVICE_UUID) || service.service_data.byteLength !== 3) continue
        const key = hex(result.dev_addr)
        if (this.phones.some(phone => phone.key === key) || this.phones.length >= 8) continue
        this.phones.push({ address: result.dev_addr.slice(0), code: hex(service.service_data), key })
        this.message = 'Match the phone code, then connect'
        this.changed()
      }
    }, { service_uuid: SERVICE_UUID })
    if (!started) {
      this.scanning = false
      this.message = 'Scan failed. Check Bluetooth permission.'
    } else {
      this.later(() => {
        this.ble.mstStopScan()
        this.scanning = false
        this.message = this.phones.length ? 'Select your phone code' : 'No phones. Start sharing, then scan again.'
        this.changed()
      }, 15000)
    }
    this.changed()
  }

  /** @param {Phone} phone */
  connect(phone) {
    if (this.closed) return
    this.generation++
    const generation = this.generation
    this.cancelTimer()
    if (this.scanning) this.ble.mstStopScan()
    this.scanning = false
    this.disconnect()
    this.selected = phone
    this.message = 'Connecting to ' + phone.code
    this.changed()
    const started = this.ble.mstConnect(phone.address, result => {
      if (this.closed || generation !== this.generation) {
        if (result.connected === 0) this.ble.mstDisconnect(result.connect_id)
        return
      }
      if (result.connected !== 0) {
        this.failed('Phone disconnected')
        return
      }
      this.connection = result.connect_id
      this.connectedAt = this.now()
      this.message = 'Secure pairing: check your phone'
      this.changed()
      this.later(() => this.failed('Secure pairing timed out'), 120000)
      const built = this.ble.mstBuildProfile({
        pair: true,
        id: result.connect_id,
        profile: 'navgo',
        dev: phone.address,
        len: 1,
        list: [{
          len: 1,
          list: [{ uuid: SERVICE_UUID, len1: 1, list: [{ uuid: DATA_UUID, len: 0, list: [] }] }]
        }]
      })
      if (!built) {
        this.failed('Could not build navigation service')
        return
      }
      const profile = this.ble.mstGetProfileInstance('navgo', result.connect_id)
      if (profile === undefined) {
        this.failed('Navigation service not found')
        return
      }
      this.profile = profile
      this.ble.mstPrepare(profile)
    })
    if (!started) this.failed('Connection request failed')
    else if (this.connection === null) this.later(() => this.failed('Connection timed out'), 20000)
  }

  read() {
    if (this.closed || this.profile === null || this.reading) return
    this.reading = true
    this.later(() => {
      this.reading = false
      this.failed('Bluetooth read timed out')
    }, 4000)
    this.ble.mstReadCharacteristic(this.profile, DATA_UUID)
  }

  /** @param {number} status */
  readError(status) {
    if (this.now() - this.connectedAt > 120000) {
      this.failed('Secure read failed (' + status + '). Check pairing.')
      return
    }
    this.navigation.clear()
    this.message = 'Secure read (' + status + '): pair and approve on phone'
    this.changed()
    this.later(() => this.read(), 1000)
  }

  /** @param {string} message */
  failed(message) {
    if (this.closed) return
    this.generation++
    this.cancelTimer()
    this.disconnect()
    this.message = message
    this.changed()
    const selected = this.selected
    if (selected) this.later(() => this.connect(selected), Math.min(30000, 1000 * Math.pow(2, this.retry++)))
  }

  disconnect() {
    this.navigation.clear()
    this.assembler.reset()
    this.reading = false
    const profile = this.profile
    const connection = this.connection
    this.profile = null
    this.connection = null
    if (profile !== null) this.ble.mstDestroyProfileInstance(profile)
    if (connection !== null) this.ble.mstDisconnect(connection)
  }

  /** @param {() => void} action @param {number} delay */
  later(action, delay) {
    this.cancelTimer()
    this.timer = setTimeout(() => {
      this.timer = null
      if (!this.closed) action()
    }, delay)
  }

  cancelTimer() {
    if (this.timer !== null) clearTimeout(this.timer)
    this.timer = null
  }

  close() {
    this.closed = true
    this.generation++
    this.cancelTimer()
    if (this.scanning) this.ble.mstStopScan()
    this.scanning = false
    this.ble.mstOffAllCb()
    this.disconnect()
  }
}
