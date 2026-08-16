// Espelha os DTOs do backend Spring Boot.
// BigDecimal chega serializado como number no JSON.

/** TotalAmountDTO { total: BigDecimal } */
export interface TotalAmount {
  total: number
}

/** AmountAndCategoryDTO { total: BigDecimal, categoryDescription: String } */
export interface AmountAndCategory {
  total: number
  categoryDescription: string
}

/** AccountDTO { accountId: Integer, description: String, amount: BigDecimal } */
export interface Account {
  accountId: number
  description: string
  amount: number
}

/** Seções navegáveis da sidebar. */
export type View = 'dashboard' | 'accounts' | 'reports' | 'recurring'

/** Intervalo de datas ISO (yyyy-mm-dd) usado nas queries de agregação. */
export type DateRange = {
  startDate: string
  endDate: string
}
