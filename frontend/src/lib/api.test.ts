import { beforeEach, describe, expect, it, vi } from 'vitest'

// O módulo guarda o token e o refresh em voo em variáveis de módulo: cada teste importa uma cópia nova.
async function loadApi() {
  vi.resetModules()
  const api = await import('./api')
  const auth = await import('./auth')
  return { ...api, ...auth }
}

function json(status: number, body: unknown = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()
let unauthorizedEvents = 0

function urlOf(input: Parameters<typeof fetch>[0]): string {
  return typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
}

function authorizationOf(init?: RequestInit): string | undefined {
  return (init?.headers as Record<string, string> | undefined)?.Authorization
}

beforeEach(() => {
  fetchMock.mockReset()
  unauthorizedEvents = 0
  const fakeWindow = new EventTarget()
  fakeWindow.addEventListener('auth:unauthorized', () => unauthorizedEvents++)
  vi.stubGlobal('window', fakeWindow)
  vi.stubGlobal('navigator', {})
  vi.stubGlobal('fetch', fetchMock)
})

describe('api session handling', () => {
  it('on 401 refreshes once and retries the original request', async () => {
    const { accountsApi, setToken, getToken } = await loadApi()
    setToken('expired')
    fetchMock.mockImplementation(async (input, init) => {
      const url = urlOf(input)
      if (url.endsWith('/auth/refresh')) return json(200, { token: 'fresh' })
      return authorizationOf(init) === 'Bearer fresh' ? json(200, [{ accountId: 1 }]) : json(401)
    })

    await expect(accountsApi.getAll()).resolves.toEqual([{ accountId: 1 }])

    const refreshCalls = fetchMock.mock.calls.filter(([input]) => urlOf(input).endsWith('/auth/refresh'))
    expect(refreshCalls).toHaveLength(1)
    expect(refreshCalls[0][1]?.credentials).toBe('include')
    expect(getToken()).toBe('fresh')
    expect(unauthorizedEvents).toBe(0)
  })

  it('concurrent 401s share a single refresh', async () => {
    const { accountsApi, categoriesApi, setToken } = await loadApi()
    setToken('expired')
    fetchMock.mockImplementation(async (input, init) => {
      if (urlOf(input).endsWith('/auth/refresh')) {
        await new Promise((resolve) => setTimeout(resolve, 10))
        return json(200, { token: 'fresh' })
      }
      return authorizationOf(init) === 'Bearer fresh' ? json(200, []) : json(401)
    })

    await Promise.all([accountsApi.getAll(), categoriesApi.list(), accountsApi.getAll()])

    const refreshCalls = fetchMock.mock.calls.filter(([input]) => urlOf(input).endsWith('/auth/refresh'))
    expect(refreshCalls).toHaveLength(1)
  })

  it('logs out only when the refresh itself is rejected with 401', async () => {
    const { accountsApi, setToken, getToken } = await loadApi()
    setToken('expired')
    fetchMock.mockResolvedValue(json(401))

    await expect(accountsApi.getAll()).rejects.toThrow('401')

    expect(unauthorizedEvents).toBe(1)
    expect(getToken()).toBeNull()
  })

  it('a network error does not clear the session nor fire the unauthorized event', async () => {
    const { accountsApi, NetworkError, setToken, getToken } = await loadApi()
    setToken('valid')
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(accountsApi.getAll()).rejects.toBeInstanceOf(NetworkError)

    expect(unauthorizedEvents).toBe(0)
    expect(getToken()).toBe('valid')
  })

  it('a network error during the refresh keeps the session', async () => {
    const { accountsApi, setToken, getToken } = await loadApi()
    setToken('expired')
    fetchMock.mockImplementation(async (input) => {
      if (urlOf(input).endsWith('/auth/refresh')) throw new TypeError('Failed to fetch')
      return json(401)
    })

    await expect(accountsApi.getAll()).rejects.toThrow()

    expect(unauthorizedEvents).toBe(0)
    expect(getToken()).toBe('expired')
  })

  it('a 5xx/429 from the refresh keeps the session', async () => {
    const { accountsApi, setToken, getToken } = await loadApi()
    setToken('expired')
    fetchMock.mockImplementation(async (input) =>
      urlOf(input).endsWith('/auth/refresh') ? json(429) : json(401),
    )

    await expect(accountsApi.getAll()).rejects.toThrow('429')

    expect(unauthorizedEvents).toBe(0)
    expect(getToken()).toBe('expired')
  })

  it('refreshSession on app load restores the access token from the cookie', async () => {
    const { refreshSession, getToken } = await loadApi()
    fetchMock.mockResolvedValue(json(200, { token: 'restored' }))

    await expect(refreshSession()).resolves.toBe(true)

    expect(getToken()).toBe('restored')
  })

  it('the access token is never written to localStorage', async () => {
    const storage = { setItem: vi.fn(), getItem: vi.fn(), removeItem: vi.fn() }
    vi.stubGlobal('localStorage', storage)
    const { authApi, setToken } = await loadApi()
    fetchMock.mockResolvedValue(json(200, { token: 'jwt' }))

    const { token } = await authApi.login('a@b.c', 'x')
    setToken(token)

    expect(storage.setItem).not.toHaveBeenCalled()
  })

  it('password reset errors surface the backend code without touching the session', async () => {
    const { authApi, ApiError, setToken, getToken } = await loadApi()
    setToken('valid')
    fetchMock.mockResolvedValue(json(400, { error: 'TOKEN_EXPIRED' }))

    const error = await authApi.confirmPasswordReset('t', 'new-password-1').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as InstanceType<typeof ApiError>).code).toBe('TOKEN_EXPIRED')
    expect(fetchMock.mock.calls.filter(([input]) => urlOf(input).endsWith('/auth/refresh'))).toHaveLength(0)
    expect(unauthorizedEvents).toBe(0)
    expect(getToken()).toBe('valid')
  })

  it('a 202/204 from the password reset endpoints resolves without parsing a body', async () => {
    const { authApi } = await loadApi()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 202 }))
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(authApi.requestPasswordReset('a@b.c')).resolves.toBeUndefined()
    await expect(authApi.confirmPasswordReset('t', 'new-password-1')).resolves.toBeUndefined()
  })
})
