// Minimal declarations for the documented Zepp phone runtimes, not browser APIs.
interface ZeppSettingsStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}
declare const settings: { settingsStorage: ZeppSettingsStorage }
declare function AppSideService(definition: object): void
type SettingsNode = object
declare function AppSettingsPage(definition: {
  build(props: { settingsStorage: ZeppSettingsStorage }): SettingsNode
}): void
declare function View(props: { style: Record<string, string> }, children: SettingsNode[]): SettingsNode
declare function Text(props: { style?: Record<string, string> }, text: string): SettingsNode
declare function TextInput(props: {
  label: string
  labelStyle?: Record<string, string>
  placeholder: string
  value: string
  onChange(value: string): void
}): SettingsNode
declare function Button(props: { label: string, style?: Record<string, string>, onClick(): void }): SettingsNode
