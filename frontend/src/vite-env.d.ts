/// <reference types="vite/client" />

/** Variáveis de ambiente lidas pelo frontend (injetadas no build pelo Vite). */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  /** Email de suporte exibido para quem não vinculou o Telegram. */
  readonly VITE_SUPPORT_EMAIL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
