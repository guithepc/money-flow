/**
 * Access token (JWT curto) guardado só em memória: nenhum script da página
 * consegue lê-lo do localStorage. A sessão sobrevive a reloads pelo cookie
 * HttpOnly de refresh, trocado por um novo access token em `/auth/refresh`.
 */
let accessToken: string | null = null

// Versões antigas guardavam o JWT no localStorage; apaga o resto que ficou.
const LEGACY_TOKEN_KEY = 'money-flow:token'
try {
  localStorage.removeItem(LEGACY_TOKEN_KEY)
} catch {
  // localStorage indisponível (modo privado, cookies bloqueados): nada a limpar.
}

export function getToken(): string | null {
  return accessToken
}

export function setToken(token: string): void {
  accessToken = token
}

export function clearToken(): void {
  accessToken = null
}
