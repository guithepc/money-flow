import { useEffect, useState, type ReactNode } from 'react'
import { LanguageContext, type Lang } from '@/lib/i18n'

/** Idioma das telas traduzidas (onboarding, gravar, menu). */
export function LanguageProvider({ initial, children }: { initial: Lang; children: ReactNode }) {
  const [lang, setLang] = useState<Lang>(initial)
  useEffect(() => setLang(initial), [initial])
  useEffect(() => {
    document.documentElement.lang = lang === 'pt' ? 'pt-BR' : 'en'
  }, [lang])
  return <LanguageContext.Provider value={{ lang, setLang }}>{children}</LanguageContext.Provider>
}
