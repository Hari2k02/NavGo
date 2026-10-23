import test from 'node:test'
import assert from 'node:assert/strict'
import { NavigationClient, REQUEST_MS } from '../lib/client.js'

const flush = () => new Promise(resolve => setImmediate(resolve))
function response(requestId, state = 1) {
  return { version: 1, requestId, body: Buffer.from([state, 2, ...Buffer.from('200 m\0Turn left\0Soon\0')]).toString('hex') }
}
function harness() {
  let now = 100
  const requests = []
  const client = new NavigationClient(id => new Promise((resolve, reject) => {
    requests.push({ id, resolve, reject })
  }), () => {}, () => now)
  client.start()
  return { client, requests, setTime: value => { now = value } }
}

test('polls one request at a time and only accepts correlated fresh navigation', async () => {
  const { client, requests } = harness()
  client.tick()
  client.retry()
  assert.equal(requests.length, 1)
  requests[0].resolve(response(requests[0].id))
  await flush()
  assert.equal(client.navigation.current(100).instruction, 'Turn left')
  client.tick()
  assert.equal(requests.length, 2)
  assert.notEqual(requests[0].id, requests[1].id)
  requests[1].resolve(response(requests[0].id))
  await flush()
  assert.equal(client.navigation.current(100), null, 'Replayed envelope must clear guidance')
})

test('watchdog clears guidance and late completion never restores it', async () => {
  const { client, requests, setTime } = harness()
  requests[0].resolve(response(requests[0].id))
  await flush()
  client.tick()
  setTime(100 + REQUEST_MS)
  client.tick()
  assert.equal(client.navigation.current(100 + REQUEST_MS), null)
  requests[1].resolve(response(requests[1].id))
  await flush()
  assert.equal(client.navigation.current(100 + REQUEST_MS), null)
  client.tick()
  assert.equal(requests.length, 3)
  client.close()
  requests[2].resolve(response(requests[2].id))
  await flush()
  assert.equal(client.navigation.current(100 + REQUEST_MS), null)
})

test('rejection, unavailable state, key failure and clock regression cannot leave live guidance', async () => {
  const { client, requests, setTime } = harness()
  requests[0].resolve(response(requests[0].id))
  await flush()
  client.tick()
  requests[1].reject(new Error('Disconnected'))
  await flush()
  assert.equal(client.navigation.current(100), null)
  client.tick()
  requests[2].resolve({ requestId: requests[2].id, error: 'UNAUTHORIZED' })
  await flush()
  assert.match(client.message, /new key/)
  client.tick()
  requests[3].resolve(response(requests[3].id, 3))
  await flush()
  assert.equal(client.navigation.current(100).state, 3)
  client.tick()
  setTime(99)
  requests[4].resolve(response(requests[4].id))
  await flush()
  assert.equal(client.navigation.current(99), null)
})
