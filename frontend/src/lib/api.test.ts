import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  accountsApi,
  authApi,
  ownerApi,
  reportsApi,
  transactionsApi,
  UNAUTHORIZED_EVENT,
} from './api'
import { getToken, setToken } from './auth'
import {
  bodyHasNoIdentity,
  calledBody,
  calledInit,
  calledUrl,
  fakeResponse,
  fetchMock,
  urlHasNoIdentity,
} from '../test/helpers'

const RANGE = { startDate: '2026-01-01', endDate: '2026-01-31' }

/** Reads the Authorization header from a fetch call's init, regardless of shape. */
function authHeader(call = 0): string | undefined {
  const headers = calledInit(call).headers as Record<string, string> | undefined
  if (!headers) return undefined
  return headers.Authorization ?? headers.authorization
}

beforeEach(() => {
  localStorage.clear()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({})))
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('authHeaders injection', () => {
  it('includes Authorization: Bearer <token> when a token is stored', async () => {
    setToken('my-jwt')
    await accountsApi.getAll()
    expect(authHeader()).toBe('Bearer my-jwt')
  })

  it('sends NO Authorization header when no token is stored', async () => {
    await accountsApi.getAll()
    expect(authHeader()).toBeUndefined()
  })

  it('injects Authorization on POST requests too', async () => {
    setToken('post-jwt')
    await ownerApi.telegramInvite()
    expect(authHeader()).toBe('Bearer post-jwt')
  })
})

describe('401 handling', () => {
  it('clears the token and dispatches auth:unauthorized on 401', async () => {
    setToken('expired')
    const listener = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, listener)
    fetchMock().mockResolvedValueOnce(fakeResponse({}, { status: 401, statusText: 'Unauthorized' }))

    await expect(accountsApi.getAll()).rejects.toThrow()

    expect(listener).toHaveBeenCalledTimes(1)
    expect(getToken()).toBeNull()
    window.removeEventListener(UNAUTHORIZED_EVENT, listener)
  })

  it('a non-ok (500) throws with the path and does NOT clear the token', async () => {
    setToken('still-valid')
    const listener = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, listener)
    fetchMock().mockResolvedValueOnce(
      fakeResponse({}, { status: 500, statusText: 'Internal Server Error' }),
    )

    await expect(accountsApi.getAll()).rejects.toThrow('/accounts')

    expect(listener).not.toHaveBeenCalled()
    expect(getToken()).toBe('still-valid')
    window.removeEventListener(UNAUTHORIZED_EVENT, listener)
  })

  it('a successful response resolves with the parsed JSON body', async () => {
    fetchMock().mockResolvedValueOnce(fakeResponse([{ id: 1, name: 'Checking' }]))
    const accounts = await accountsApi.getAll()
    expect(accounts).toEqual([{ id: 1, name: 'Checking' }])
  })
})

describe('client never leaks user identity', () => {
  beforeEach(() => {
    // identity would be leaked regardless of auth state; test with a token present.
    setToken('identity-token')
  })

  it('authApi.login POSTs only {email,password}, no identity key', async () => {
    fetchMock().mockResolvedValueOnce(fakeResponse({ token: 't' }))
    await authApi.login('a@b.com', 'secret')

    expect(calledUrl()).toContain('/auth/login')
    expect(calledInit().method).toBe('POST')
    expect(calledBody()).toEqual({ email: 'a@b.com', password: 'secret' })
    expect(bodyHasNoIdentity(calledBody())).toEqual([])
    expect(urlHasNoIdentity(calledUrl())).toEqual([])
  })

  it('authApi.register POSTs only {name,email,password}, no identity key', async () => {
    fetchMock().mockResolvedValueOnce(fakeResponse({ token: 't' }))
    await authApi.register('Jane', 'a@b.com', 'secret')
    expect(calledBody()).toEqual({ name: 'Jane', email: 'a@b.com', password: 'secret' })
    expect(bodyHasNoIdentity(calledBody())).toEqual([])
  })

  it('reportsApi.totalSpent WITHOUT accountId carries only the date range', async () => {
    await reportsApi.totalSpent(RANGE)
    const qs = new URL(calledUrl()).searchParams
    expect(qs.get('startDate')).toBe('2026-01-01')
    expect(qs.get('endDate')).toBe('2026-01-31')
    expect(qs.has('accountId')).toBe(false)
    expect(urlHasNoIdentity(calledUrl())).toEqual([])
  })

  it('reportsApi.totalSpent WITH accountId adds exactly accountId (no identity)', async () => {
    await reportsApi.totalSpent(RANGE, 42)
    const qs = new URL(calledUrl()).searchParams
    expect(qs.get('accountId')).toBe('42')
    expect(urlHasNoIdentity(calledUrl())).toEqual([])
  })

  it('transactionsApi.list carries no identity in the URL', async () => {
    await transactionsApi.list(RANGE, 7)
    expect(calledUrl()).toContain('/transactions')
    expect(urlHasNoIdentity(calledUrl())).toEqual([])
  })

  it('accountsApi.getAll carries no identity in the URL', async () => {
    await accountsApi.getAll()
    expect(calledUrl()).toContain('/accounts')
    expect(urlHasNoIdentity(calledUrl())).toEqual([])
  })

  it('ownerApi.telegramInvite POSTs an empty body (identity from JWT)', async () => {
    fetchMock().mockResolvedValueOnce(fakeResponse({ link: 'https://t.me/x' }))
    await ownerApi.telegramInvite()
    expect(calledUrl()).toContain('/owner/telegram-invite')
    expect(calledInit().method).toBe('POST')
    expect(calledBody()).toEqual({})
    expect(bodyHasNoIdentity(calledBody())).toEqual([])
    expect(urlHasNoIdentity(calledUrl())).toEqual([])
  })
})

describe('login flow wiring', () => {
  it('authApi.login returns {token} and storing it authenticates later calls', async () => {
    fetchMock().mockResolvedValueOnce(fakeResponse({ token: 'logged-in-jwt' }))
    const { token } = await authApi.login('a@b.com', 'pw')
    expect(token).toBe('logged-in-jwt')

    setToken(token)
    fetchMock().mockResolvedValueOnce(fakeResponse([]))
    await accountsApi.getAll()
    expect(authHeader(1)).toBe('Bearer logged-in-jwt')
  })
})
