export const MAX_BODY = 461
export const STALE_MS = 15000

/** @param {unknown} value @param {string} requestId */
export function decodeEnvelope(value, requestId) {
  if (!value || typeof value !== 'object' || !('version' in value) || value.version !== 1
    || !('requestId' in value) || value.requestId !== requestId
    || !('body' in value) || typeof value.body !== 'string' || value.body.length > MAX_BODY * 2
    || !/^(?:[0-9a-f]{2}){5,}$/.test(value.body)) {
    throw new Error('Invalid relay response')
  }
  const bytes = new Uint8Array(value.body.length / 2)
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(value.body.slice(i * 2, i * 2 + 2), 16)
  return decodeBody(bytes)
}

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
  const limits = [32, 280, 144]
  let start = 2
  for (let i = 2; i < bytes.length; i++) {
    if (bytes[i] === 0) {
      if (fields.length >= 3 || i - start > limits[fields.length]) throw new Error('Navigation field exceeds limit')
      fields.push(utf8(bytes.slice(start, i)))
      start = i + 1
    }
  }
  if (fields.length !== 3 || start !== bytes.length) throw new Error('Invalid navigation fields')
  return { state: bytes[0], turn: bytes[1], distance: fields[0], instruction: fields[1], summary: fields[2] }
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
