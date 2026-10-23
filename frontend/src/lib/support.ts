/**
 * Email de suporte: recuperação de senha sem Telegram vinculado e pedidos de privacidade
 * (Termos de uso e Política de privacidade). Única definição, usada por todas as telas.
 * `||` (e não `??`): um ARG vazio no build do Docker vira string vazia.
 */
export const SUPPORT_EMAIL: string = import.meta.env.VITE_SUPPORT_EMAIL || 'guilhermepcstore@gmail.com'
