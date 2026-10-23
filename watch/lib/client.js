import { NavigationState, decodeEnvelope } from './protocol.js'

export const REQUEST_MS = 4000
const messages = {
  SETUP_REQUIRED: 'Paste key in Zepp settings',
  UNAUTHORIZED: 'Copy new key into Zepp',
  PHONE_OFF: 'Start sharing on phone',
  PHONE_TIMEOUT: 'Phone relay timed out',
  BUSY: 'Relay busy / reopen Zepp',
  INVALID_RESPONSE: 'Update both NavGo apps',
  CLOSED: 'Reopen NavGo in Zepp'
}

export class NavigationClient {
  /** @param {(id: string) => Promise<unknown>} request @param {() => void} changed @param {() => number} [now] */
  constructor(request, changed, now = Date.now) {
    this.request = request
    this.changed = changed
    this.now = now
    this.navigation = new NavigationState()
    this.message = 'Connecting through Zepp'
    this.closed = true
    this.sequence = 0
    this.prefix = now().toString(36) + '-' + Math.floor(Math.random() * 0x100000000).toString(36)
    /** @type {{id: string, startedAt: number, expired: boolean} | null} */
    this.pending = null
  }

  start() {
    this.closed = false
    this.tick()
  }

  retry() {
    // Do not create a second native request while the first is still outstanding.
    if (this.pending) return
    this.navigation.clear()
    this.message = 'Connecting through Zepp'
    this.tick()
  }

  tick() {
    if (this.closed) return
    const now = this.now()
    if (this.pending) {
      if (!this.pending.expired && (now < this.pending.startedAt || now - this.pending.startedAt >= REQUEST_MS)) {
        this.pending.expired = true
        this.fail('Zepp link timed out')
      }
      return
    }
    const flight = { id: this.prefix + '-' + (++this.sequence).toString(36), startedAt: now, expired: false }
    this.pending = flight
    this.poll(flight)
  }

  /** @param {{id: string, startedAt: number, expired: boolean}} flight */
  async poll(flight) {
    try {
      const result = await this.request(flight.id)
      if (this.closed || this.pending !== flight) return
      const now = this.now()
      if (flight.expired || now < flight.startedAt || now - flight.startedAt >= REQUEST_MS) {
        this.fail('Zepp link timed out')
      } else if (result && typeof result === 'object' && 'error' in result
        && 'requestId' in result && result.requestId === flight.id) {
        const error = result.error
        this.fail(typeof error === 'string' && Object.prototype.hasOwnProperty.call(messages, error)
          ? messages[/** @type {keyof typeof messages} */ (error)] : 'Invalid relay response')
      } else {
        const navigation = decodeEnvelope(result, flight.id)
        this.navigation.update(navigation, now)
        this.message = 'Connected through Zepp'
        this.changed()
      }
    } catch {
      if (!this.closed && this.pending === flight) this.fail('Check Zepp link / retry')
    } finally {
      if (this.pending === flight) this.pending = null
    }
  }

  /** @param {string} message */
  fail(message) {
    this.navigation.clear()
    this.message = message
    this.changed()
  }

  close() {
    this.closed = true
    this.pending = null
    this.navigation.clear()
  }
}
