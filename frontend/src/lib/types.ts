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

/** operation ENUM do backend (EXPENSE, INCOME, TRANSFER). */
export type OperationType = 'EXPENSE' | 'INCOME' | 'TRANSFER'

/**
 * TransactionDTO { id, description, amount, date, categoryDescription, operationType }
 * Retornado por `GET /api/transactions`.
 */
export interface Transaction {
  id: number
  description: string
  amount: number
  date: string
  categoryDescription: string
  operationType: OperationType
}

/**
 * Ponto de série temporal diária (balance history).
 * Contrato do endpoint futuro `GET /transactions/balance-history`.
 */
export interface TimeSeriesPoint {
  date: string
  income: number
  expense: number
  net: number
}

/**
 * Income × expense agregado por mês.
 * Contrato do endpoint futuro `GET /transactions/income-expense-monthly`.
 */
export interface MonthlyIncomeExpense {
  month: string
  income: number
  expense: number
}

/** Seções navegáveis da sidebar. */
export type View = 'dashboard' | 'accounts' | 'reports' | 'recurring'

/** Intervalo de datas ISO (yyyy-mm-dd) usado nas queries de agregação. */
export type DateRange = {
  startDate: string
  endDate: string
}
