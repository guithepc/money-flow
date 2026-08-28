// Paleta dos gráficos derivada dos tokens do tema (index.css).
// Mantém contraste no fundo escuro e reaproveita a identidade do projeto.

/** Papéis semânticos — batem com --color-data / --color-negative / --color-brand. */
export const CHART = {
  income: '#3fcf5f', // --color-data (verde)
  expense: '#f0616d', // --color-negative (vermelho)
  brand: '#eab308', // --color-brand (âmbar)
  grid: '#242a26', // --color-edge
  axis: '#9ba5a0', // --color-fg-muted
  surface: '#141816', // --color-surface (tooltip bg)
} as const

/**
 * Sequência de cores pro donut de categorias. Deriva dos tokens principais +
 * tons intermediários, alternando quente/frio pra distinguir fatias no escuro.
 */
export const CATEGORY_PALETTE = [
  '#3fcf5f', // verde
  '#eab308', // âmbar
  '#f0616d', // vermelho
  '#34b851', // verde forte
  '#f6a623', // laranja
  '#7ee8fa', // ciano
  '#b79df6', // lilás
  '#9ba5a0', // cinza (overflow/"outros")
] as const

/** Cor da fatia por índice, com wrap-around. */
export function categoryColor(index: number): string {
  return CATEGORY_PALETTE[index % CATEGORY_PALETTE.length]
}
