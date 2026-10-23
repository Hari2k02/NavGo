import { decodeEnvelope } from './protocol.js'

/** @typedef {{url: string, method: string, headers: Record<string, string>, timeout: number}} FetchOptions */

export class PhoneBridge {
  /** @param {(options: FetchOptions) => Promise<unknown>} fetchLocal @param {() => unknown} getKey */
  constructor(fetchLocal, getKey) {
    this.fetchLocal = fetchLocal
    this.getKey = getKey
    this.busy = false
    this.closed = false
  }

  /** @param {unknown} requestId */
  async snapshot(requestId) {
    if (typeof requestId !== 'string' || !/^[a-z0-9-]{1,64}$/.test(requestId)) {
      return { requestId: '', error: 'INVALID_RESPONSE' }
    }
    /** @param {string} error */
    const failure = error => ({ requestId, error })
    if (this.closed) return failure('CLOSED')
    const key = this.getKey()
    if (typeof key !== 'string' || !/^[0-9a-f]{64}$/.test(key)) return failure('SETUP_REQUIRED')
    if (this.busy) return failure('BUSY')
    this.busy = true
    /** @type {ReturnType<typeof setTimeout> | undefined} */
    let timer
    let started = false
    try {
      const network = this.fetchLocal({
        url: 'http://127.0.0.1:27341/v1/navigation?requestId=' + requestId,
        method: 'GET',
        headers: { Authorization: 'Bearer ' + key, 'Cache-Control': 'no-store' },
        timeout: 2500
      }).then(value => ({ value }), () => ({ error: 'PHONE_OFF' }))
        .finally(() => { this.busy = false })
      started = true
      // Native fetch timeout support varies. Keep busy until native fetch settles,
      // even if our response deadline expires, so retries cannot accumulate fetches.
      const result = await Promise.race([
        network,
        new Promise(resolve => { timer = setTimeout(() => resolve({ error: 'PHONE_TIMEOUT' }), 2500) })
      ])
      if (this.closed) return failure('CLOSED')
      if (this.getKey() !== key) return failure('SETUP_REQUIRED')
      if (!result || typeof result !== 'object') return failure('INVALID_RESPONSE')
      if ('error' in result && typeof result.error === 'string') return failure(result.error)
      if (!('value' in result)) return failure('INVALID_RESPONSE')
      const response = result.value
      if (!response || typeof response !== 'object' || !('status' in response)) return failure('INVALID_RESPONSE')
      if (response.status === 401) return failure('UNAUTHORIZED')
      if (response.status !== 200 || !('body' in response)) return failure('INVALID_RESPONSE')
      let body = response.body
      if (typeof body === 'string') {
        if (body.length > 2048) return failure('INVALID_RESPONSE')
        body = JSON.parse(body)
      }
      decodeEnvelope(body, requestId)
      return body
    } catch {
      if (!started) this.busy = false
      return failure('INVALID_RESPONSE')
    } finally {
      if (timer !== undefined) clearTimeout(timer)
    }
  }

  close() {
    this.closed = true
  }
}
