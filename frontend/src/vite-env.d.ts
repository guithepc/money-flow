/// <reference types="vite/client" />

/** Variáveis de ambiente lidas pelo frontend (injetadas no build pelo Vite). */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  /** Email de suporte (recuperação de senha sem Telegram e pedidos de privacidade). */
  readonly VITE_SUPPORT_EMAIL?: string
  /** Para onde o usuário vai depois de excluir a conta. */
  readonly VITE_LANDING_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
