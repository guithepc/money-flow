import { afterEach, describe, expect, it } from 'vitest'
import { clearToken, getToken, setToken } from './auth'

const TOKEN_KEY = 'money-flow:token'

afterEach(() => {
  localStorage.clear()
})

describe('auth token storage', () => {
  it('getToken returns null when nothing is stored', () => {
    expect(getToken()).toBeNull()
  })

  it('setToken persists the token under the expected key', () => {
    setToken('abc.def.ghi')
    expect(localStorage.getItem(TOKEN_KEY)).toBe('abc.def.ghi')
  })

  it('getToken reads back what setToken stored', () => {
    setToken('round-trip-token')
    expect(getToken()).toBe('round-trip-token')
  })

  it('clearToken removes the stored token', () => {
    setToken('to-be-cleared')
    clearToken()
    expect(getToken()).toBeNull()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })
})
