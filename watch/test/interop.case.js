import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { delimiter, join } from 'node:path'
import { PhoneBridge } from '../lib/bridge.js'
import { NavigationClient } from '../lib/client.js'

test('authenticated Java HTTP response traverses side bridge and watch client', async () => {
  const core = fileURLToPath(new URL('../../android/core/build/classes/java/', import.meta.url))
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java'
  const bridge = new PhoneBridge(async options => {
    const id = new URL(options.url).searchParams.get('requestId')
    const result = spawnSync(java, ['-cp', join(core, 'main') + delimiter + join(core, 'test'),
      'app.navgo.core.ProtocolFixture', id], { encoding: 'utf8', timeout: 10000 })
    assert.equal(result.error, undefined, 'Run :core:testClasses and provide JAVA_HOME first')
    assert.equal(result.status, 0, result.stderr)
    return { status: 200, body: result.stdout }
  }, () => 'ab'.repeat(32))
  const client = new NavigationClient(id => bridge.snapshot(id), () => {}, () => 100)
  client.start()
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(client.navigation.current(100), { state: 1, turn: 2, distance: '200 m',
    instruction: 'Turn left onto मुख्य सड़क 🚶', summary: '12 min · 4.2 km' })
  client.close()
  bridge.close()
})
