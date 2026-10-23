AppSettingsPage({
  build(props) {
    const key = props.settingsStorage.getItem('setupKey')
    const valid = typeof key === 'string' && /^[0-9a-f]{64}$/.test(key)
    /** @param {SettingsNode} child */
    const row = child => View({ style: { display: 'block', marginBottom: '20px' } }, [child])
    return View({ style: { padding: '20px', background: '#f5f5f5', color: '#101612', lineHeight: '1.5' } }, [
      row(Text({ style: { fontSize: '24px', fontWeight: 'bold' } }, 'NavGo setup / 0.2.2')),
      row(Text({}, 'Copy the private setup key from NavGo on Android. Tap the green control below to open the key editor, paste the key, then confirm.')),
      row(TextInput({
        label: 'Tap to paste setup key',
        labelStyle: {
          display: 'block', padding: '16px', background: '#c9fa73', color: '#101612',
          border: '1px solid #65852f', borderRadius: '12px', fontSize: '18px',
          fontWeight: 'bold', textAlign: 'center'
        },
        placeholder: '64-character key from Android',
        value: '',
        onChange(value) {
          const next = value.trim().toLowerCase()
          if (/^[0-9a-f]{64}$/.test(next)) props.settingsStorage.setItem('setupKey', next)
          else {
            props.settingsStorage.removeItem('setupKey')
            props.settingsStorage.setItem('setupError', 'Invalid key. Copy all 64 characters from Android.')
            return
          }
          props.settingsStorage.removeItem('setupError')
        }
      })),
      row(Text({}, valid ? 'Key saved (hidden). Start sharing on Android, then open NavGo on the watch.'
        : props.settingsStorage.getItem('setupError') || 'Setup key required.')),
      row(Button({
        label: 'Remove saved key',
        style: { display: 'block', width: '100%', padding: '12px', fontSize: '16px' },
        onClick() {
          props.settingsStorage.removeItem('setupKey')
          props.settingsStorage.removeItem('setupError')
        }
      })),
      row(Text({ style: { fontSize: '14px' } }, 'Keep the key private. Zepp handles the watch connection and can access forwarded navigation. Reset the key in Android to revoke access; removing it here alone is not revocation. NavGo uses no remote server.'))
    ])
  }
})
