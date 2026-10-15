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

/** currency ENUM do backend. */
export type Currency = 'BRL' | 'EUR' | 'USD'

/** AccountDTO { accountId: Integer, description: String, amount: BigDecimal, currency: CurrencyEnum } */
export interface Account {
  accountId: number
  description: string
  amount: number
  currency: Currency
}

/** operation ENUM do backend (EXPENSE, INCOME, TRANSFER). */
export type OperationType = 'EXPENSE' | 'INCOME' | 'TRANSFER'

/**
 * TransactionDTO { id, description, amount, date, categoryDescription, operationType, currency, categoryId }
 * Retornado por `GET /api/transactions`.
 */
export interface Transaction {
  id: number
  description: string
  amount: number
  date: string
  categoryDescription: string
  operationType: OperationType
  /** Moeda da conta da transação. */
  currency: Currency
  categoryId: number
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
export type View = 'dashboard' | 'accounts' | 'reports' | 'settings'

/** category_type ENUM do backend. */
export type CategoryType = 'EXPENSE' | 'INCOME'

/** CategoryDTO { categoryDescription, categoryId, type } — `GET /api/categories`. */
export interface Category {
  categoryId: number
  categoryDescription: string
  type: CategoryType
}

/** TransactionUpdateRequestDTO — corpo do `PUT /api/transactions/{id}`. */
export interface TransactionUpdate {
  description: string
  categoryId: number
  amount: number
}

/** OwnerProfileDTO { name: String, email: String, telegramLinked: boolean } */
export interface OwnerProfile {
  name: string
  email: string
  telegramLinked: boolean
}

/** TelegramInviteResponseDTO { link: String } — deep link para conectar o bot. */
export interface TelegramInvite {
  link: string
}

/** Intervalo de datas ISO (yyyy-mm-dd) usado nas queries de agregação. */
export type DateRange = {
  startDate: string
  endDate: string
}
