import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TranslatorPage } from '../pages/TranslatorPage'
import { result } from './fixtures'

function mockFetch(impl: (url: string, init?: RequestInit) => Promise<Response>) {
  const fn = vi.fn(impl)
  vi.stubGlobal('fetch', fn)
  return fn
}

const json = (body: unknown, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))

function deferredResponse() {
  let resolve!: (response: Response) => void
  const promise = new Promise<Response>((r) => { resolve = r })
  return { promise, resolve: async (body: unknown, status = 200) => {
    await act(async () => { resolve(await json(body, status)) })
  } }
}

describe('TranslatorPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('validates empty and over-long input without calling the API', () => {
    const fetchFn = mockFetch(() => json(result))
    render(<TranslatorPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter some English text')

    fireEvent.change(screen.getByLabelText('English text'), { target: { value: 'a'.repeat(501) } })
    expect(screen.getByText('501 / 500')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    expect(screen.getByRole('alert')).toHaveTextContent('500 characters or fewer')
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it('posts the text, then renders the player and the result details', async () => {
    const fetchFn = mockFetch(() => json(result))
    render(<TranslatorPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Thank you, my friend Zebra!' }))
    expect(screen.getByLabelText('English text')).toHaveValue('Thank you, my friend Zebra!')
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    expect(screen.getByRole('button', { name: 'Translating…' })).toBeDisabled()

    await screen.findByRole('region', { name: 'Sign sequence player' })
    const [url, init] = fetchFn.mock.calls[0]
    expect(url).toBe('/api/translate')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ text: 'Thank you, my friend Zebra!' })

    expect(screen.getByText('Translation ready: 7 sign items.')).toBeInTheDocument()
    expect(screen.getByText('Fingerspelled · 5 letters')).toBeInTheDocument()
    expect(screen.getByText('4.2 ms')).toBeInTheDocument()
    expect(screen.getByText(/fingerspelled letter by letter: ZEBRA/)).toBeInTheDocument()
    expect(screen.getAllByText('Fingerspelling (ZEBRA)')).toHaveLength(5)
    expect(screen.getByText('I LIKE ZEBRA')).toBeInTheDocument()
  })

  it('shows the server error message and can retry', async () => {
    let calls = 0
    mockFetch(() => (++calls === 1 ? json({ status: 400, message: 'Text must be 500 characters or fewer.' }, 400) : json(result)))
    render(<TranslatorPage initialText="hello" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    expect(await screen.findByText('Text must be 500 characters or fewer.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await screen.findByRole('region', { name: 'Sign sequence player' })
  })

  it('reports an unreachable server', async () => {
    mockFetch(() => Promise.reject(new TypeError('Failed to fetch')))
    render(<TranslatorPage initialText="hello" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    expect(await screen.findByText(/Cannot reach the Signify server/)).toBeInTheDocument()
  })

  it('clears input and results', async () => {
    mockFetch(() => json(result))
    render(<TranslatorPage initialText="hello" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    await screen.findByRole('region', { name: 'Sign sequence player' })
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }))
    expect(screen.getByLabelText('English text')).toHaveValue('')
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Sign sequence player' })).toBeNull())
  })

  it.each(['edit', 'example'])('invalidates old results on %s without automatically translating', async (action) => {
    const fetchFn = mockFetch(() => json(result))
    render(<TranslatorPage initialText="D" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    await screen.findByRole('region', { name: 'Sign sequence player' })
    if (action === 'edit') {
      fireEvent.change(screen.getByLabelText('English text'), { target: { value: 'E' } })
    } else {
      fireEvent.click(screen.getByRole('button', { name: 'Thank you, my friend Zebra!' }))
    }
    expect(screen.queryByRole('region', { name: 'Sign sequence player' })).toBeNull()
    expect(screen.queryByText('Translation result')).toBeNull()
    expect(fetchFn).toHaveBeenCalledTimes(1)
  })

  it.each([200, 400])('ignores a superseded request even when it completes with HTTP %s', async (status) => {
    const first = deferredResponse()
    const second = deferredResponse()
    const fetchFn = mockFetch(() => fetchFn.mock.calls.length === 1 ? first.promise : second.promise)
    render(<TranslatorPage initialText="D" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    fireEvent.change(screen.getByLabelText('English text'), { target: { value: 'E' } })
    expect(fetchFn.mock.calls[0][1]?.signal?.aborted).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    await second.resolve({ ...result, originalText: 'E' })
    expect(screen.getByText('Translation result')).toBeInTheDocument()
    await first.resolve(status === 200 ? { ...result, originalText: 'outdated D' } : { message: 'outdated error' }, status)
    expect(screen.queryByText('outdated D')).toBeNull()
    expect(screen.queryByText('outdated error')).toBeNull()
    expect(screen.getByLabelText('English text')).toHaveValue('E')
    expect(screen.getByText('E', { selector: '.original-text' })).toBeInTheDocument()
  })

  it.each(['edit', 'clear', 'example'])('cancels pending output on %s', async (action) => {
    const pending = deferredResponse()
    mockFetch(() => pending.promise)
    render(<TranslatorPage initialText="D" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    if (action === 'edit') fireEvent.change(screen.getByLabelText('English text'), { target: { value: 'E' } })
    else fireEvent.click(screen.getByRole('button', { name: action === 'clear' ? 'Clear' : 'Thank you, my friend Zebra!' }))
    await pending.resolve(result)
    expect(screen.queryByRole('region', { name: 'Sign sequence player' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Translate' })).toBeEnabled()
  })

  it('remounts a fresh paused player after another translation', async () => {
    mockFetch(() => json(result))
    render(<TranslatorPage initialText="hello" />)
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    await screen.findByRole('region', { name: 'Sign sequence player' })
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    fireEvent.click(screen.getByRole('button', { name: 'Translate' }))
    await screen.findByRole('region', { name: 'Sign sequence player' })
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1')
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
    expect(screen.getByLabelText('Speed')).toHaveValue('1')
  })
})
