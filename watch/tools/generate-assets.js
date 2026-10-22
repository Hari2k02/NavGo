import { mkdirSync, writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'
import { fileURLToPath } from 'node:url'

const target = fileURLToPath(new URL('../assets/trex3.w480-r/', import.meta.url))
mkdirSync(target, { recursive: true })

function crc32(bytes) {
  let crc = 0xffffffff
  for (const byte of bytes) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const body = Buffer.concat([Buffer.from(type), data])
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}

function image(size, segments, icon = false) {
  const rows = Buffer.alloc(size * (1 + size * 4))
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const nx = x / size * 96
      const ny = y / size * 96
      const foreground = segments.some(([x1, y1, x2, y2]) => {
        const dx = x2 - x1
        const dy = y2 - y1
        const t = Math.max(0, Math.min(1, ((nx - x1) * dx + (ny - y1) * dy) / (dx * dx + dy * dy || 1)))
        return Math.hypot(nx - x1 - t * dx, ny - y1 - t * dy) <= 4
      })
      const pos = y * (1 + size * 4) + 1 + x * 4
      rows[pos] = foreground ? 201 : 16
      rows[pos + 1] = foreground ? 250 : 22
      rows[pos + 2] = foreground ? 115 : 18
      rows[pos + 3] = foreground || icon ? 255 : 0
    }
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(size)
  header.writeUInt32BE(size, 4)
  header[8] = 8
  header[9] = 6
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header), chunk('IDAT', deflateSync(rows)), chunk('IEND', Buffer.alloc(0))])
}

const straight = [[48, 78, 48, 18], [48, 18, 28, 38], [48, 18, 68, 38]]
const left = [[68, 78, 68, 38], [68, 38, 22, 38], [22, 38, 40, 20], [22, 38, 40, 56]]
const slight = [[62, 78, 62, 52], [62, 52, 28, 18], [28, 18, 28, 43], [28, 18, 53, 18]]
const sharp = [[70, 78, 70, 22], [70, 22, 24, 68], [24, 68, 24, 40], [24, 68, 52, 68]]
const mirror = segments => segments.map(([x1, y1, x2, y2]) => [96 - x1, y1, 96 - x2, y2])
const unknown = [[31, 30, 40, 20], [40, 20, 58, 20], [58, 20, 66, 30],
  [66, 30, 48, 48], [48, 48, 48, 57], [48, 73, 48, 74]]
const uturn = [[68, 78, 68, 28], [68, 28, 55, 18], [55, 18, 34, 18],
  [34, 18, 22, 28], [22, 28, 22, 56], [22, 56, 8, 42], [22, 56, 36, 42]]
const roundabout = [[48, 80, 48, 66], [48, 66, 26, 58], [26, 58, 23, 36],
  [23, 36, 42, 23], [42, 23, 64, 30], [64, 30, 70, 51],
  [70, 51, 85, 51], [85, 51, 74, 38], [85, 51, 74, 64]]
const merge = [...straight, [18, 74, 48, 44]]
const exit = [[40, 78, 40, 18], [40, 52, 77, 21], [77, 21, 52, 21], [77, 21, 77, 46]]
const shapes = [unknown, straight, left, mirror(left), slight, mirror(slight),
  sharp, mirror(sharp), uturn, roundabout, merge, exit]
shapes.forEach((shape, i) => writeFileSync(new URL(`../assets/trex3.w480-r/turn-${i}.png`, import.meta.url), image(96, shape)))
writeFileSync(new URL('../assets/trex3.w480-r/icon.png', import.meta.url), image(248, straight, true))
console.log('Generated 12 maneuver icons and app icon.')
