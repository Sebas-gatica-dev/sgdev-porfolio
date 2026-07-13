export type Theme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'sg-portfolio-theme'

export function getInitialTheme(): Theme {
  return 'light'
}

export function persistTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
  window.localStorage.setItem(THEME_STORAGE_KEY, theme)
}
