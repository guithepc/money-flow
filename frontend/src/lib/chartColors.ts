// Paleta dos gráficos derivada dos tokens do tema (index.css).
// Mantém contraste no fundo escuro e reaproveita a identidade do projeto.

/** Papéis semânticos — batem com --color-data / --color-negative / --color-brand. */
export const CHART = {
  income: '#3DDC84', // --color-data (verde)
  expense: '#FF7A6B', // --color-negative (coral)
  brand: '#E2FF2E', // --color-brand (lime)
  grid: 'rgba(255,255,255,0.06)',
  axis: 'rgba(255,255,255,0.45)',
  surface: '#1D212B', // --color-surface-raised (tooltip bg)
  edge: 'rgba(255,255,255,0.12)', // --color-edge
} as const

/**
 * Sequência de cores pro donut de categorias. A última é reservada pra
 * "Outros" (agrupamento da cauda).
 */
export const CATEGORY_PALETTE = [
  '#E2FF2E', // lime
  '#3DDC84', // verde
  '#7C83FF', // índigo claro
  '#FF7A6B', // coral
  '#FFC24B', // âmbar
  '#5FD4F4', // ciano
  '#C9A7FF', // lilás
] as const

export const OTHERS_COLOR = 'rgba(255,255,255,0.35)'

/** Cor da fatia por índice, com wrap-around. */
export function categoryColor(index: number): string {
  return CATEGORY_PALETTE[index % CATEGORY_PALETTE.length]
}
