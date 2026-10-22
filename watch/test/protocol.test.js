import test from 'node:test'
import assert from 'node:assert/strict'
import { FrameAssembler, NavigationState, decodeBody, STALE_MS } from '../lib/protocol.js'

function frames(sequence = 1, text = 'Turn left', state = 1, turn = 2) {
  const body = Buffer.concat([Buffer.from([state, turn]), Buffer.from('200 m\0' + text + '\0' + '12 min\0')])
  const count = Math.ceil(body.length / 14)
  return Array.from({ length: count }, (_, index) => {
    const frame = Uint8Array.from([0x4e, 1, sequence >> 8, sequence & 255, index, count,
      ...body.subarray(index * 14, index * 14 + 14)])
    return frame.buffer
  })
}

function assemble(assembler, packets, now = 0) {
  let result = null
  for (const frame of packets) result = assembler.push(frame, now)
  return result
}

test('reassembles Unicode split across BLE fragments', () => {
  const value = assemble(new FrameAssembler(), frames(1, 'Turn left onto मुख्य सड़क 🚶'))
  assert.deepEqual(value, { state: 1, turn: 2, distance: '200 m',
    instruction: 'Turn left onto मुख्य सड़क 🚶', summary: '12 min' })
})

test('missing, duplicated and out-of-order chunks cannot publish partial routes', () => {
  const packets = frames(1, 'A long instruction to guarantee several frames')
  const assembler = new FrameAssembler()
  assert.equal(assemble(assembler, [packets[0], packets[2]]), null)
  assert.equal(assembler.push(packets[0], 0), null)
  assert.equal(assembler.push(packets[0], 0), null)
  assert.equal(assemble(assembler, packets.slice(1))?.instruction, 'A long instruction to guarantee several frames')
})

test('incomplete packet is replaced by a new sequence', () => {
  const assembler = new FrameAssembler()
  assembler.push(frames(1)[0], 0)
  assert.equal(assemble(assembler, frames(2, 'Turn right'))?.instruction, 'Turn right')
})

test('rejects replay, old sequence and accepts sequence wrap', () => {
  const assembler = new FrameAssembler()
  assert.ok(assemble(assembler, frames(65535)))
  assert.equal(assemble(assembler, frames(65535)), null)
  assert.ok(assemble(assembler, frames(0)))
  assert.equal(assemble(assembler, frames(65534)), null)
})

test('malformed headers, lengths, enums, field layouts and UTF-8 fail explicitly', () => {
  const assembler = new FrameAssembler()
  assert.throws(() => assembler.push(Uint8Array.of(0x4e, 2, 0, 1, 0, 1, 0).buffer, 0))
  assert.throws(() => assembler.push(Uint8Array.of(0x4e, 1, 0, 1, 0, 255, 0).buffer, 0))
  assert.throws(() => decodeBody(Uint8Array.of(4, 0, 0, 0, 0)))
  assert.throws(() => decodeBody(Uint8Array.of(1, 12, 0, 0, 0)))
  assert.throws(() => decodeBody(Uint8Array.of(1, 2, 0, 0, 0, 5)))
  assert.throws(() => decodeBody(Uint8Array.of(1, 2, 0xff, 0, 0, 0)))
  assert.throws(() => decodeBody(new Uint8Array(462)))
})

test('expired fragmented transfers cannot refresh guidance', () => {
  const assembler = new FrameAssembler()
  const packets = frames()
  assembler.push(packets[0], 0)
  assert.throws(() => assembler.push(packets[1], 5001), /timed out/)
})

test('navigation disappears at exact freshness deadline and clock regression', () => {
  const state = new NavigationState()
  const nav = assemble(new FrameAssembler(), frames())
  assert.equal(state.current(0), null)
  state.update(nav, 100)
  assert.deepEqual(state.current(100 + STALE_MS - 1), nav)
  assert.equal(state.current(100 + STALE_MS), null)
  assert.equal(state.current(99), null)
  state.clear()
  assert.equal(state.current(101), null)
})

test('reset permits a new authenticated connection sequence', () => {
  const assembler = new FrameAssembler()
  assemble(assembler, frames(100))
  assembler.reset()
  assert.ok(assemble(assembler, frames(0)))
})

test('all 4 states and 12 maneuvers decode', () => {
  for (let state = 0; state < 4; state++) {
    for (let turn = 0; turn < 12; turn++) {
      const nav = assemble(new FrameAssembler(), frames(1, 'Instruction', state, turn))
      assert.equal(nav.state, state)
      assert.equal(nav.turn, turn)
    }
  }
})
