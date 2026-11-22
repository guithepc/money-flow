import { afterEach, describe, expect, it, vi } from 'vitest'
import { browserLang, toLang } from './i18n'

afterEach(() => vi.unstubAllGlobals())

describe('browserLang', () => {
  it('is Portuguese only for pt* browsers, like the bot with Telegram', () => {
    vi.stubGlobal('navigator', { language: 'pt-BR' })
    expect(browserLang()).toBe('pt')
    vi.stubGlobal('navigator', { language: 'en-GB' })
    expect(browserLang()).toBe('en')
    vi.stubGlobal('navigator', { language: 'es-ES' })
    expect(browserLang()).toBe('en')
  })
})

describe('toLang', () => {
  it('accepts only the two app languages', () => {
    expect(toLang('pt')).toBe('pt')
    expect(toLang('en')).toBe('en')
    expect(toLang(null)).toBeNull()
    expect(toLang('fr')).toBeNull()
  })
})
