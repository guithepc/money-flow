import { createContext, useContext } from 'react'

/** Idiomas do app: os mesmos do bot (`owner.language`). Cada tela tem seu dicionário `{ pt, en }`. */
export type Lang = 'pt' | 'en'

/** Antes da pessoa escolher: português só para navegador em pt*, como o bot faz com o Telegram. */
export function browserLang(): Lang {
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('pt') ? 'pt' : 'en'
}

export function toLang(code: string | null | undefined): Lang | null {
  return code === 'pt' || code === 'en' ? code : null
}

export interface LanguageContextValue {
  lang: Lang
  setLang: (lang: Lang) => void
}

export const LanguageContext = createContext<LanguageContextValue>({ lang: 'pt', setLang: () => {} })

export function useLanguage(): LanguageContextValue {
  return useContext(LanguageContext)
}
