import test from 'node:test'
import assert from 'node:assert/strict'
import { PhoneBridge } from '../lib/bridge.js'

const key = 'ab'.repeat(32)
const envelope = { version: 1, requestId: 'test-1', body: '0102323030206d005475726e206c6566740000' }

test('fixed loopback endpoint uses an authorization header, not a credential in URL', async () => {
  for (const body of [envelope, JSON.stringify(envelope)]) {
    const bridge = new PhoneBridge(async options => {
      assert.equal(options.url, 'http://127.0.0.1:27341/v1/navigation?requestId=test-1')
      assert.equal(options.headers.Authorization, 'Bearer ' + key)
      assert.equal(options.headers['Cache-Control'], 'no-store')
      return { status: 200, body }
    }, () => key)
    assert.deepEqual(await bridge.snapshot('test-1'), envelope)
  }
})

test('missing key and malformed IDs never reach native fetch', async () => {
  let calls = 0
  const bridge = new PhoneBridge(async () => { calls++; return {} }, () => '')
  assert.equal((await bridge.snapshot('test-1')).error, 'SETUP_REQUIRED')
  assert.equal((await bridge.snapshot('../path')).error, 'INVALID_RESPONSE')
  assert.equal(calls, 0)
})

test('HTTP failure, invalid JSON, oversized packets and mismatched IDs fail closed', async () => {
  for (const [value, error] of [
    [{ status: 401, body: {} }, 'UNAUTHORIZED'],
    [{ status: 500, body: envelope }, 'INVALID_RESPONSE'],
    [{ status: 200, body: '{' }, 'INVALID_RESPONSE'],
    [{ status: 200, body: ' '.repeat(2049) }, 'INVALID_RESPONSE'],
    [{ status: 200, body: { ...envelope, requestId: 'old' } }, 'INVALID_RESPONSE'],
    [{ status: 200, body: { ...envelope, body: '00'.repeat(462) } }, 'INVALID_RESPONSE']
  ]) {
    const bridge = new PhoneBridge(async () => value, () => key)
    assert.equal((await bridge.snapshot('test-1')).error, error)
  }
  const bridge = new PhoneBridge(async () => { throw new Error('Offline') }, () => key)
  assert.equal((await bridge.snapshot('test-1')).error, 'PHONE_OFF')
})

test('single in-flight fetch and key removal invalidate an outstanding response', async () => {
  let resolve
  let currentKey = key
  const bridge = new PhoneBridge(() => new Promise(done => { resolve = done }), () => currentKey)
  const pending = bridge.snapshot('test-1')
  assert.equal((await bridge.snapshot('test-2')).error, 'BUSY')
  currentKey = null
  resolve({ status: 200, body: envelope })
  assert.equal((await pending).error, 'SETUP_REQUIRED')
  bridge.close()
  assert.equal((await bridge.snapshot('test-3')).error, 'CLOSED')
})

test('a fetch ignoring native timeout cannot accumulate retries or return a late route', async () => {
  let resolve
  let calls = 0
  const bridge = new PhoneBridge(() => {
    calls++
    return new Promise(done => { resolve = done })
  }, () => key)
  assert.equal((await bridge.snapshot('test-1')).error, 'PHONE_TIMEOUT')
  assert.equal((await bridge.snapshot('test-2')).error, 'BUSY')
  assert.equal(calls, 1)
  resolve({ status: 200, body: envelope })
  await new Promise(done => setImmediate(done))
  assert.equal(bridge.busy, false)
})
