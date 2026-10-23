import test from 'node:test'
import assert from 'node:assert/strict'
import { NavigationState, decodeBody, decodeEnvelope, STALE_MS } from '../lib/protocol.js'

function body(text = 'Turn left', state = 1, turn = 2) {
  return Buffer.concat([Buffer.from([state, turn]), Buffer.from('200 m\0' + text + '\0' + '12 min\0')])
}

test('decodes bounded Unicode navigation', () => {
  assert.deepEqual(decodeBody(body('Turn left onto मुख्य सड़क 🚶')),
    { state: 1, turn: 2, distance: '200 m', instruction: 'Turn left onto मुख्य सड़क 🚶', summary: '12 min' })
})

test('malformed lengths, enums, field layouts and UTF-8 fail explicitly', () => {
  for (const bytes of [
    Uint8Array.of(4, 0, 0, 0, 0), Uint8Array.of(1, 12, 0, 0, 0),
    Uint8Array.of(1, 2, 0, 0, 0, 5), Uint8Array.of(1, 2, 0xff, 0, 0, 0),
    new Uint8Array(462), body('x'.repeat(281)),
    Buffer.concat([Buffer.from([1, 2]), Buffer.from('x'.repeat(33) + '\0a\0b\0')])
  ]) assert.throws(() => decodeBody(bytes))
})

test('rejects old request IDs, unsupported versions and malformed hex envelopes', () => {
  const value = { version: 1, requestId: 'a-1', body: body().toString('hex') }
  assert.equal(decodeEnvelope(value, 'a-1').instruction, 'Turn left')
  assert.throws(() => decodeEnvelope(value, 'a-2'))
  for (const patch of [{ version: 2 }, { body: '010200000' }, { body: 'gg'.repeat(5) }, { body: '00'.repeat(462) }]) {
    assert.throws(() => decodeEnvelope({ ...value, ...patch }, 'a-1'))
  }
})

test('navigation disappears at exact freshness deadline and clock regression', () => {
  const state = new NavigationState()
  const nav = decodeBody(body())
  assert.equal(state.current(0), null)
  state.update(nav, 100)
  assert.deepEqual(state.current(100 + STALE_MS - 1), nav)
  assert.equal(state.current(100 + STALE_MS), null)
  assert.equal(state.current(99), null)
  state.clear()
  assert.equal(state.current(101), null)
})

test('all 4 states and 12 maneuvers decode', () => {
  for (let state = 0; state < 4; state++) {
    for (let turn = 0; turn < 12; turn++) {
      const nav = decodeBody(body('Instruction', state, turn))
      assert.equal(nav.state, state)
      assert.equal(nav.turn, turn)
    }
  }
})
