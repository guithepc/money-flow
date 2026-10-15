import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useAuth } from './useAuth'
import { UNAUTHORIZED_EVENT } from '@/lib/api'
import { getToken, setToken } from '@/lib/auth'
import { fakeResponse, fetchMock } from '../test/helpers'

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({})))
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('useAuth guard logic', () => {
  it('starts unauthenticated when no token is present', () => {
    const { result } = renderHook(() => useAuth())
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('starts authenticated when a token already exists', () => {
    setToken('existing')
    const { result } = renderHook(() => useAuth())
    expect(result.current.isAuthenticated).toBe(true)
  })

  it('flips to unauthenticated when a UNAUTHORIZED_EVENT fires', async () => {
    setToken('existing')
    const { result } = renderHook(() => useAuth())
    expect(result.current.isAuthenticated).toBe(true)

    act(() => {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    })
    await waitFor(() => expect(result.current.isAuthenticated).toBe(false))
  })
})

describe('useAuth login flow', () => {
  it('login success stores the token and authenticates', async () => {
    fetchMock().mockResolvedValueOnce(fakeResponse({ token: 'fresh-jwt' }))
    const { result } = renderHook(() => useAuth())

    await act(async () => {
      await result.current.login('a@b.com', 'pw')
    })

    expect(getToken()).toBe('fresh-jwt')
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.error).toBeNull()
  })

  it('login failure sets a generic error and does not authenticate', async () => {
    fetchMock().mockResolvedValueOnce(
      fakeResponse({}, { status: 401, statusText: 'Unauthorized' }),
    )
    const { result } = renderHook(() => useAuth())

    await act(async () => {
      await result.current.login('a@b.com', 'wrong')
    })

    expect(getToken()).toBeNull()
    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.error).toBe('Email ou senha inválidos.')
  })

  it('logout clears the token and deauthenticates', async () => {
    setToken('existing')
    const { result } = renderHook(() => useAuth())
    expect(result.current.isAuthenticated).toBe(true)

    act(() => {
      result.current.logout()
    })

    expect(getToken()).toBeNull()
    expect(result.current.isAuthenticated).toBe(false)
  })
})
