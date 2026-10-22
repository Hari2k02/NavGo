import test from 'node:test'
import assert from 'node:assert/strict'
import { NavigationClient } from '../lib/client.js'
import { SERVICE_UUID, DATA_UUID } from '../lib/protocol.js'

function fakeBle() {
  const callbacks = {}
  const calls = []
  const ble = {
    mstOnPrepare: cb => { callbacks.prepare = cb; return true },
    mstOnCharaValueArrived: cb => { callbacks.value = cb; return true },
    mstOnCharaReadComplete: cb => { callbacks.complete = cb; return true },
    mstStartScan: cb => { callbacks.scan = cb; calls.push('scan'); return true },
    mstStopScan: () => { calls.push('stopScan'); return true },
    mstConnect: (address, cb) => { callbacks.connect = cb; calls.push('connect'); return true },
    mstDisconnect: id => { calls.push(['disconnect', id]); return true },
    mstBuildProfile: profile => { calls.push(['build', profile]); return true },
    mstGetProfileInstance: () => 8,
    mstPrepare: id => { calls.push(['prepare', id]) },
    mstReadCharacteristic: (profile, uuid) => { calls.push(['read', profile, uuid]) },
    mstDestroyProfileInstance: id => { calls.push(['destroy', id]) },
    mstOffAllCb: () => { calls.push('off') }
  }
  return { ble, callbacks, calls }
}

const phone = { address: new Uint8Array([1, 2, 3, 4, 5, 6]).buffer, code: 'ABCDEF', key: '010203040506' }

test('discovers only bounded, correctly identified NavGo phones', () => {
  const { ble, callbacks } = fakeBle()
  const client = new NavigationClient(ble, () => {})
  try {
    client.start()
    const result = { dev_addr: phone.address, service_data_array: [
      { uuid: SERVICE_UUID, service_data: Uint8Array.of(0xab, 0xcd, 0xef).buffer }] }
    callbacks.scan(result)
    callbacks.scan(result)
    callbacks.scan({ ...result, service_data_array: [{ uuid: 'other', service_data: new ArrayBuffer(3) }] })
    assert.equal(client.phones.length, 1)
    assert.equal(client.phones[0].code, 'ABCDEF')
    assert.equal(client.selected, null, 'Discovery must not silently connect')
  } finally {
    client.close()
  }
})

test('builds paired profile and prepares correct pointer after explicit selection', () => {
  const { ble, callbacks, calls } = fakeBle()
  const client = new NavigationClient(ble, () => {})
  try {
    client.start()
    client.connect(phone)
    callbacks.connect({ connected: 0, connect_id: 7, dev_addr: phone.address })
    assert.equal(calls.find(call => Array.isArray(call) && call[0] === 'build')[1].pair, true)
    assert.deepEqual(calls.find(call => Array.isArray(call) && call[0] === 'prepare'), ['prepare', 8])
    callbacks.prepare(8, 0)
    client.read()
    assert.deepEqual(calls.at(-1), ['read', 8, DATA_UUID])
    callbacks.complete(8, DATA_UUID, 5)
    assert.match(client.message, /pair and approve/)
    assert.equal(client.navigation.current(0), null)
  } finally {
    client.close()
  }
})

test('stale connection callbacks are disconnected and cannot revive a closed page', () => {
  const { ble, callbacks, calls } = fakeBle()
  const client = new NavigationClient(ble, () => {})
  client.start()
  client.connect(phone)
  const oldCallback = callbacks.connect
  client.close()
  oldCallback({ connected: 0, connect_id: 7, dev_addr: phone.address })
  assert.equal(client.connection, null)
  assert.deepEqual(calls.at(-1), ['disconnect', 7])
  assert.equal(client.timer, null)
})

test('disconnect immediately clears route and cleans up GATT resources', () => {
  const { ble, callbacks, calls } = fakeBle()
  const client = new NavigationClient(ble, () => {}, () => 100)
  try {
    client.start()
    client.connect(phone)
    callbacks.connect({ connected: 0, connect_id: 7, dev_addr: phone.address })
    client.navigation.update({ state: 1, turn: 2, instruction: 'Turn left', distance: '', summary: '' }, 100)
    callbacks.connect({ connected: 2, connect_id: 7, dev_addr: phone.address })
    assert.equal(client.navigation.current(100), null)
    assert.ok(calls.some(call => Array.isArray(call) && call[0] === 'destroy' && call[1] === 8))
    assert.equal(client.connection, null)
  } finally {
    client.close()
  }
})
