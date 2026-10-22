import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { delimiter, join } from 'node:path'
import { FrameAssembler } from '../lib/protocol.js'

test('Java Android encoder interoperates with actual watch JavaScript decoder', () => {
  const core = fileURLToPath(new URL('../../android/core/build/classes/java/', import.meta.url))
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java'
  const result = spawnSync(java, ['-cp', join(core, 'main') + delimiter + join(core, 'test'),
    'app.navgo.core.ProtocolFixture'], { encoding: 'utf8' })
  assert.equal(result.error, undefined, 'Run :core:testClasses and provide JAVA_HOME first')
  assert.equal(result.status, 0, result.stderr)
  const assembler = new FrameAssembler()
  let navigation = null
  for (const hex of JSON.parse(result.stdout)) {
    navigation = assembler.push(Uint8Array.from(Buffer.from(hex, 'hex')).buffer, 100)
  }
  assert.deepEqual(navigation, { state: 1, turn: 2, distance: '200 m',
    instruction: 'Turn left onto मुख्य सड़क 🚶', summary: '12 min · 4.2 km' })
})
