import { useState, type FormEvent } from 'react'
import { AuthError, AuthShell, AuthSubmit, TermsCheckbox } from '@/components/AuthShell'
import { ownerApi } from '@/lib/api'
import { TERMS_VERSION } from '@/lib/legal'

interface AcceptTermsProps {
  onAccepted: () => void
  onLogout: () => void
}

/**
 * Pedido de aceite para quem ainda não aceitou a versão atual dos termos
 * (contas anteriores aos termos, ou depois de uma nova versão). Bloqueia o painel até aceitar.
 */
export function AcceptTerms({ onAccepted, onLogout }: AcceptTermsProps) {
  const [accepted, setAccepted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!accepted) return
    setLoading(true)
    setError(null)
    try {
      await ownerApi.acceptTerms(TERMS_VERSION)
      onAccepted()
    } catch {
      setError('Não foi possível registrar o aceite. Tente novamente.')
      setLoading(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Atualização"
      title="Termos e privacidade"
      subtitle="Publicamos os Termos de uso e a Política de privacidade do Money Flow. Leia e aceite para continuar usando o painel."
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && <AuthError message={error} />}

        <TermsCheckbox checked={accepted} onChange={setAccepted} />

        <AuthSubmit disabled={loading || !accepted}>{loading ? 'Salvando…' : 'Continuar'}</AuthSubmit>

        <p className="text-sm text-ink/65">
          Não quer aceitar?{' '}
          <button
            type="button"
            onClick={onLogout}
            className="font-semibold text-indigo underline-offset-4 hover:underline"
          >
            Sair
          </button>
        </p>
      </form>
    </AuthShell>
  )
}
