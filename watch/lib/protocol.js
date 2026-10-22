export const SERVICE_UUID = 'a8e70001-8d9b-4c2f-a51b-2e52d72f6300'
export const DATA_UUID = 'a8e70002-8d9b-4c2f-a51b-2e52d72f6300'
export const MAX_BODY = 461
export const STALE_MS = 15000

/** @typedef {{state: number, turn: number, distance: string, instruction: string, summary: string}} Navigation */

/** @param {Uint8Array} bytes */
function utf8(bytes) {
  let encoded = ''
  for (let i = 0; i < bytes.length; i++) encoded += '%' + bytes[i].toString(16).padStart(2, '0')
  return decodeURIComponent(encoded)
}

/** @param {Uint8Array} bytes @returns {Navigation} */
export function decodeBody(bytes) {
  if (bytes.length < 5 || bytes.length > MAX_BODY || bytes[0] > 3 || bytes[1] > 11) {
    throw new Error('Invalid navigation body')
  }
  const fields = []
  let start = 2
  for (let i = 2; i < bytes.length; i++) {
    if (bytes[i] === 0) {
      fields.push(utf8(bytes.slice(start, i)))
      start = i + 1
    }
  }
  if (fields.length !== 3 || start !== bytes.length) throw new Error('Invalid navigation fields')
  return { state: bytes[0], turn: bytes[1], distance: fields[0], instruction: fields[1], summary: fields[2] }
}

export class FrameAssembler {
  constructor() {
    /** @type {number | null} */
    this.sequence = null
    this.count = 0
    this.next = 0
    /** @type {number[]} */
    this.bytes = []
    /** @type {number | null} */
    this.completed = null
    this.startedAt = 0
  }

  reset() {
    this.sequence = null
    this.count = 0
    this.next = 0
    this.bytes = []
    this.completed = null
    this.startedAt = 0
  }

  /** @param {ArrayBuffer} buffer @param {number} now @returns {Navigation | null} */
  push(buffer, now) {
    const frame = new Uint8Array(buffer)
    if (frame.length < 7 || frame.length > 20 || frame[0] !== 0x4e || frame[1] !== 1) {
      throw new Error('Invalid navigation frame')
    }
    const sequence = (frame[2] << 8) | frame[3]
    const index = frame[4]
    const count = frame[5]
    if (count < 1 || count > 33 || index >= count || (index < count - 1 && frame.length !== 20)) {
      throw new Error('Invalid navigation fragmentation')
    }
    if (index === 0) {
      if (this.completed !== null) {
        const delta = (sequence - this.completed + 65536) % 65536
        if (delta === 0 || delta >= 32768) return null
      }
      this.sequence = sequence
      this.count = count
      this.next = 0
      this.bytes = []
      this.startedAt = now
    }
    if (sequence !== this.sequence || count !== this.count || index !== this.next) return null
    if (now - this.startedAt > 5000) throw new Error('Navigation transfer timed out')
    for (let i = 6; i < frame.length; i++) this.bytes.push(frame[i])
    if (this.bytes.length > MAX_BODY) throw new Error('Navigation packet exceeds limit')
    this.next++
    if (this.next !== this.count) return null
    const result = decodeBody(new Uint8Array(this.bytes))
    this.completed = sequence
    this.sequence = null
    this.bytes = []
    return result
  }
}

export class NavigationState {
  constructor() {
    /** @type {Navigation | null} */
    this.navigation = null
    this.receivedAt = -1
  }

  /** @param {Navigation} navigation @param {number} now */
  update(navigation, now) {
    this.navigation = navigation
    this.receivedAt = now
  }

  /** @param {number} now */
  current(now) {
    return this.receivedAt < 0 || now - this.receivedAt >= STALE_MS || now < this.receivedAt
      ? null : this.navigation
  }

  clear() {
    this.navigation = null
    this.receivedAt = -1
  }
}
