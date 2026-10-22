const path = require('node:path')
const root = path.dirname(require.resolve('@zeppos/zeus-cli/package.json'))

// Zeus' module-alias/register otherwise resolves against the app's package.json.
require('module-alias').addAlias('zeppos-app-utils', path.join(root, 'private-modules', 'zeppos-app-utils'))
require(path.join(root, 'bin', 'main.js'))
