import { vi } from 'vitest'

/** Builds a realistic fake Response for the mocked `fetch`. */
export function fakeResponse<T>(
  body: T,
  init: { status?: number; statusText?: string } = {},
): Response {
  const status = init.status ?? 200
  const statusText = init.statusText ?? 'OK'
  return {
    status,
    statusText,
    ok: status >= 200 && status < 300,
    json: () => Promise.resolve(body),
  } as unknown as Response
}

/** The mocked global fetch, typed for convenient assertions. */
export function fetchMock() {
  return globalThis.fetch as unknown as ReturnType<typeof vi.fn>
}

/** Returns the URL string of the Nth fetch call (default: first). */
export function calledUrl(call = 0): string {
  const arg = fetchMock().mock.calls[call][0]
  return arg instanceof URL ? arg.toString() : String(arg)
}

/** Returns the request init (2nd arg) of the Nth fetch call. */
export function calledInit(call = 0): RequestInit {
  return (fetchMock().mock.calls[call][1] ?? {}) as RequestInit
}

/** Parses the JSON body sent on the Nth fetch call, or null. */
export function calledBody(call = 0): unknown {
  const init = calledInit(call)
  return init.body ? JSON.parse(init.body as string) : null
}

const IDENTITY_KEYS = ['ownerId', 'owner', 'userId', 'user_id', 'ownerid', 'userid']

/** Asserts a URL querystring carries no user-identity key. */
export function urlHasNoIdentity(url: string): string[] {
  const qs = new URL(url).searchParams
  const leaks: string[] = []
  for (const key of qs.keys()) {
    const low = key.toLowerCase()
    if (IDENTITY_KEYS.some((k) => k.toLowerCase() === low)) leaks.push(key)
  }
  return leaks
}

/** Asserts a JSON body object carries no user-identity key (deep). */
export function bodyHasNoIdentity(body: unknown): string[] {
  const leaks: string[] = []
  const walk = (val: unknown) => {
    if (val && typeof val === 'object') {
      for (const [k, v] of Object.entries(val)) {
        if (IDENTITY_KEYS.some((id) => id.toLowerCase() === k.toLowerCase())) leaks.push(k)
        walk(v)
      }
    }
  }
  walk(body)
  return leaks
}
