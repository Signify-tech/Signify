import { describe, expect, it, vi, afterEach } from 'vitest'
import { validateProductionApiBase } from '../api/productionBase'

describe('production API configuration', () => {
  afterEach(() => { vi.unstubAllEnvs(); vi.resetModules() })

  it('accepts and normalizes a remote HTTPS origin', () => {
    expect(validateProductionApiBase(' https://signify-api.onrender.com/// ')).toBe('https://signify-api.onrender.com')
  })

  it.each([undefined, '', 'http://localhost:8080', 'https://localhost', 'https://127.0.0.1',
    'https://[::1]', 'http://signify-api.onrender.com', 'https://signify-api.onrender.com/api',
    'https://user:password@example.com', 'https://example.com?token=secret', 'https://example.com#api'])
  ('rejects invalid production origin %s', (value) => {
    expect(() => validateProductionApiBase(value)).toThrow()
  })

  it('uses the remote origin for API calls and relative media while preserving absolute media URLs', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://signify-api.onrender.com/')
    vi.resetModules()
    const { api, mediaSrc } = await import('../api/client')
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'))
    try {
      await api.translate('A')
      expect(fetch).toHaveBeenCalledWith('https://signify-api.onrender.com/api/translate', expect.anything())
      expect(mediaSrc('/api/media/189')).toBe('https://signify-api.onrender.com/api/media/189')
      expect(mediaSrc('https://cdn.example.com/A.jpg')).toBe('https://cdn.example.com/A.jpg')
    } finally {
      fetch.mockRestore()
    }
  })
})
