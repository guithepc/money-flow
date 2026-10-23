/** Regra de senha — a mesma do backend para cadastro e redefinição. */
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_BYTES = 72

export const PASSWORD_RULE_MESSAGE = 'A senha precisa ter pelo menos 8 caracteres (máximo de 72).'

/** Retorna a mensagem de erro se a senha não cumpre a regra, ou `null` se está ok. */
export function validatePassword(password: string): string | null {
  if (password.length < PASSWORD_MIN_LENGTH) return PASSWORD_RULE_MESSAGE
  // O limite é em bytes (bcrypt), não em caracteres — acentos contam mais de 1.
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) return PASSWORD_RULE_MESSAGE
  return null
}
