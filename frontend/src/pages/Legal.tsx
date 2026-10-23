import { useEffect, useState } from 'react'
import { ArrowLeft, TrendingUp } from 'lucide-react'
import { LEGAL_PATHS, LEGAL_UPDATED_AT, TERMS_VERSION } from '@/lib/legal'
import {
  LEGAL_DOCUMENTS,
  type LegalDocumentKind,
  type LegalLang,
} from '@/lib/legalContent'
import { cn } from '@/lib/utils'

const UI = {
  pt: {
    eyebrow: 'Documento legal',
    version: 'Versão',
    updated: 'atualizada em',
    back: 'Voltar ao Money Flow',
    other: { terms: 'Política de privacidade', privacy: 'Termos de uso' },
  },
  en: {
    eyebrow: 'Legal',
    version: 'Version',
    updated: 'updated on',
    back: 'Back to Money Flow',
    other: { terms: 'Privacy policy', privacy: 'Terms of use' },
  },
} as const

/** ?lang=pt|en vence; senão segue o idioma do navegador. */
function initialLang(): LegalLang {
  const fromQuery = new URLSearchParams(window.location.search).get('lang')
  if (fromQuery === 'pt' || fromQuery === 'en') return fromQuery
  return navigator.language.toLowerCase().startsWith('pt') ? 'pt' : 'en'
}

function formatDate(isoDate: string, lang: LegalLang): string {
  // Meio-dia local: evita que o fuso volte a data um dia.
  return new Date(`${isoDate}T12:00:00`).toLocaleDateString(lang === 'pt' ? 'pt-BR' : 'en-US', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/** Termos de uso / Política de privacidade — rota pública, com troca PT/EN. */
export function Legal({ kind }: { kind: LegalDocumentKind }) {
  const [lang, setLang] = useState<LegalLang>(initialLang)
  const doc = LEGAL_DOCUMENTS[kind][lang]
  const ui = UI[lang]
  const other: LegalDocumentKind = kind === 'terms' ? 'privacy' : 'terms'

  useEffect(() => {
    document.title = `${doc.title} · Money Flow`
    document.documentElement.lang = lang === 'pt' ? 'pt-BR' : 'en'
  }, [doc.title, lang])

  function switchLang(next: LegalLang) {
    setLang(next)
    const url = new URL(window.location.href)
    url.searchParams.set('lang', next)
    window.history.replaceState(null, '', url)
  }

  return (
    <div className="bg-page-grid h-screen overflow-y-auto bg-background text-fg">
      <header className="border-b border-edge">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-6 py-5">
          <a href="/" className="flex items-center gap-2.5">
            <span className="flex size-7 items-center justify-center bg-brand">
              <TrendingUp className="size-4 text-black" strokeWidth={2.6} />
            </span>
            <span className="font-display text-lg font-semibold tracking-[-0.02em]">money flow</span>
          </a>

          <div role="group" aria-label="Idioma / Language" className="flex border border-edge">
            {(['pt', 'en'] as const).map((code) => (
              <button
                key={code}
                type="button"
                onClick={() => switchLang(code)}
                aria-pressed={lang === code}
                className={cn(
                  'label-mono px-3 py-1.5 transition-colors duration-150',
                  lang === code ? 'bg-brand text-black' : 'text-fg-muted hover:text-fg',
                )}
              >
                {code}
              </button>
            ))}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-6 pt-12 pb-16">
        <div className="mb-10 flex flex-col gap-3">
          <span className="eyebrow">{ui.eyebrow}</span>
          <h1 className="font-display text-5xl leading-[52px] font-normal tracking-[-0.02em]">{doc.title}</h1>
          <p className="text-[15px] text-fg-muted">{doc.summary}</p>
          <p className="label-mono text-fg-subtle">
            {ui.version} {TERMS_VERSION} · {ui.updated} {formatDate(LEGAL_UPDATED_AT, lang)}
          </p>
        </div>

        {doc.sections.map((section, index) => (
          <section key={section.title} className="flex flex-col gap-4 border-t border-edge py-8">
            <h2 className="flex items-baseline gap-3 font-display text-2xl font-normal tracking-[-0.02em]">
              <span className="label-mono text-brand">{String(index + 1).padStart(2, '0')}</span>
              {section.title}
            </h2>
            {section.blocks.map((block, blockIndex) =>
              typeof block === 'string' ? (
                <p key={blockIndex} className="text-[15px] leading-7 text-fg-muted">
                  {block}
                </p>
              ) : (
                <ul key={blockIndex} className="flex flex-col gap-2.5">
                  {block.map((item) => (
                    <li key={item} className="flex gap-3 text-[15px] leading-7 text-fg-muted">
                      <span aria-hidden className="mt-[11px] size-[6px] shrink-0 bg-brand" />
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              ),
            )}
          </section>
        ))}

        <footer className="flex flex-wrap items-center justify-between gap-4 border-t border-edge pt-8 text-sm">
          <a href="/" className="flex items-center gap-2 text-fg-muted transition-colors duration-150 hover:text-fg">
            <ArrowLeft className="size-4" strokeWidth={1.8} />
            {ui.back}
          </a>
          <a
            href={`${LEGAL_PATHS[other]}?lang=${lang}`}
            className="font-semibold text-brand underline-offset-4 hover:underline"
          >
            {ui.other[kind]}
          </a>
        </footer>
      </main>
    </div>
  )
}
