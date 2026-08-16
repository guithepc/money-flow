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

/** Intervalo de datas ISO (yyyy-mm-dd) usado nas queries de agregação. */
export type DateRange = {
  startDate: string
  endDate: string
}
