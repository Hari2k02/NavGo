import { BaseSideService } from '@zeppos/zml/base-side'
import { PhoneBridge } from '../lib/bridge.js'

/** @type {PhoneBridge | null} */
let bridge = null

const service = BaseSideService({
  onInit() {
    bridge = new PhoneBridge(options => this.fetch(options), () => settings.settingsStorage.getItem('setupKey'))
  },
  onSettingsChange() {
    // Read the current key on every request; never cache credentials.
  },
  onRequest(request, respond) {
    if (request.method !== 'navgo.snapshot' || !bridge) {
      respond({ code: 'UNSUPPORTED', message: 'NavGo snapshot requests only' }, null)
      return
    }
    bridge.snapshot(request.params?.requestId).then(result => respond(null, result),
      () => respond(null, { requestId: request.params?.requestId, error: 'SETUP_REQUIRED' }))
  },
  onDestroy() {
    if (bridge) bridge.close()
    bridge = null
  }
})

// ZML installs plugin methods during construction, so override its proxy afterwards.
/** @param {unknown} request @param {(error: {code: string, message: string}, data: null) => void} respond */
service.httpRequestHandler = (request, respond) => {
  respond({ code: 'UNSUPPORTED', message: 'Generic HTTP requests are disabled' }, null)
}
AppSideService(service)
