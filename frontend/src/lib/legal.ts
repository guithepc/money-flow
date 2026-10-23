import type { LegalDocumentKind } from './legalContent'

/**
 * Versão publicada dos Termos de uso e da Política de privacidade (os dois andam juntos).
 * Precisa bater com `LegalTerms.CURRENT_VERSION` no backend: subir a versão pede novo aceite
 * de todo mundo no próximo login.
 */
export const TERMS_VERSION = '1.0'

/** Data da versão acima (yyyy-mm-dd). */
export const LEGAL_UPDATED_AT = '2026-10-23'

// ---- Valores citados no texto dos documentos. O email de suporte (SUPPORT_EMAIL) fica em lib/support.ts.

/** Em quantos dias os backups diários do banco expiram (dados excluídos somem dos backups depois disso). */
export const BACKUP_RETENTION_DAYS = 14

/** Limite diário de mensagens de voz por conta, citado nos Termos de uso. */
export const VOICE_DAILY_LIMIT = 20

/** Para onde o usuário vai depois de excluir a conta. Sem a env, volta para a raiz do app. */
export const LANDING_URL: string = import.meta.env.VITE_LANDING_URL || '/'

/** Caminhos públicos (sem login) de cada documento. */
export const LEGAL_PATHS: Record<LegalDocumentKind, string> = {
  terms: '/terms',
  privacy: '/privacy',
}
