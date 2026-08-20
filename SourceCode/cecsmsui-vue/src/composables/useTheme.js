const THEME_STORAGE_KEY = 'silverpilot-theme'
const LIGHT_THEME = 'light'

export const resolveInitialTheme = () => LIGHT_THEME

export const applyThemeToDocument = (_requestedTheme, documentTarget = globalThis.document) => {
  if (!documentTarget?.documentElement) return LIGHT_THEME
  documentTarget.documentElement.dataset.theme = LIGHT_THEME
  documentTarget.documentElement.style.colorScheme = LIGHT_THEME
  return LIGHT_THEME
}

export const initializeTheme = () => {
  try { globalThis.localStorage?.removeItem(THEME_STORAGE_KEY) }
  catch { /* storage can be blocked by the browser */ }
  return applyThemeToDocument(LIGHT_THEME)
}
