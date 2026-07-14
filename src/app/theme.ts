export type Theme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'sg-portfolio-theme'
const MOBILE_THEME_QUERY = '(max-width: 720px)'

export function isMobileThemeLocked() {
  return window.matchMedia(MOBILE_THEME_QUERY).matches
}

export function getInitialTheme(): Theme {
  if (isMobileThemeLocked()) {
    return 'light'
  }

  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
  if (savedTheme === 'light' || savedTheme === 'dark') {
    return savedTheme
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function persistTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
  window.localStorage.setItem(THEME_STORAGE_KEY, theme)
}
