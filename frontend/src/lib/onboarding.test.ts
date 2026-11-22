import { describe, expect, it } from 'vitest'
import { defaultCurrency, parseBalanceInput } from './onboarding'

describe('parseBalanceInput', () => {
  it('reads the Brazilian format', () => {
    expect(parseBalanceInput('1.250,90')).toBe(1250.9)
    expect(parseBalanceInput('500')).toBe(500)
    expect(parseBalanceInput('0,5')).toBe(0.5)
  })

  it('reads the dot-decimal format English speakers type', () => {
    expect(parseBalanceInput('1250.90')).toBe(1250.9)
    expect(parseBalanceInput('1,250.90')).toBe(1250.9)
  })

  it('accepts negative balances (credit cards)', () => {
    expect(parseBalanceInput('-1.500,00')).toBe(-1500)
    expect(parseBalanceInput('-300')).toBe(-300)
  })

  it('rejects anything else', () => {
    for (const bad of ['', 'abc', '12a', '1.2.3,4', '--5', '1,2345', '-'])
      expect(parseBalanceInput(bad), bad).toBeNull()
  })
})

describe('defaultCurrency', () => {
  it('suggests BRL in Portuguese and EUR in English', () => {
    expect(defaultCurrency('pt')).toBe('BRL')
    expect(defaultCurrency('en')).toBe('EUR')
  })
})
