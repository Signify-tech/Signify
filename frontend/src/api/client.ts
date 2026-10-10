import type { ApiErrorBody, Page, RuleInfo, SignCategory, TranslationResult, VocabularyEntry, VocabularyStats } from './types'

/** Empty by default: the Vite dev server proxies /api to the backend. Set VITE_API_BASE_URL for other setups. */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '')

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(API_BASE + path, {
      ...init,
      headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}) },
    })
  } catch {
    throw new ApiError(0, 'Cannot reach the Signify server. Check that the backend is running.')
  }
  if (!response.ok) {
    let message = `Request failed (${response.status}).`
    try {
      const body = (await response.json()) as ApiErrorBody
      if (body.message) message = body.message
    } catch {
      // keep the generic message
    }
    throw new ApiError(response.status, message)
  }
  return (await response.json()) as T
}

export function mediaSrc(url: string): string {
  return url.startsWith('http') ? url : API_BASE + url
}

export const api = {
  translate: (text: string, signal?: AbortSignal) =>
    request<TranslationResult>('/api/translate', { method: 'POST', body: JSON.stringify({ text }), signal }),

  vocabulary: (params: { q?: string; category?: SignCategory | ''; page: number; size: number }, signal?: AbortSignal) => {
    const search = new URLSearchParams({ page: String(params.page), size: String(params.size) })
    if (params.category) search.set('category', params.category)
    const q = params.q?.trim()
    if (q) search.set('q', q)
    return request<Page<VocabularyEntry>>(`/api/vocabulary${q ? '/search' : ''}?${search}`, { signal })
  },

  stats: (signal?: AbortSignal) => request<VocabularyStats>('/api/vocabulary/stats', { signal }),

  rules: (signal?: AbortSignal) => request<RuleInfo[]>('/api/rules', { signal }),
}
