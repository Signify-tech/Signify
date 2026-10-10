import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Page, VocabularyEntry, VocabularyStats } from '../api/types'
import { VocabularyPage } from '../pages/VocabularyPage'

const entry = (id: number, term: string, extra: Partial<VocabularyEntry> = {}): VocabularyEntry => ({
  id, term, normalizedTerm: term, category: 'WORD', verificationStatus: 'UNVERIFIED', status: 'MEDIA_MISSING',
  mediaRegistered: false, mediaAvailable: false, playable: false, verified: false, ...extra,
})

const stats: VocabularyStats = {
  totalEntries: 3, words: 2, phrases: 1, letters: 0, digits: 0, wordsAndPhrases: 3, targetWordsAndPhrases: 100,
  mediaRegistered: 1, mediaAvailable: 1, mediaMissing: 2, verified: 1, availableUnverified: 0, lettersWithMedia: 0,
  mediaCoveragePercent: 33.3,
}

describe('VocabularyPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads entries and stats from the API and searches the backend', async () => {
    const urls: string[] = []
    vi.stubGlobal('fetch', vi.fn((url: string) => {
      urls.push(url)
      let body: unknown = stats
      if (url.startsWith('/api/vocabulary/search')) {
        body = { items: [entry(2, 'hello', { status: 'VERIFIED', playable: true, verified: true, mediaAvailable: true, mediaRegistered: true, mediaType: 'VIDEO', mediaUrl: '/api/media/2' })], page: 0, size: 24, totalItems: 1, totalPages: 1 } satisfies Page<VocabularyEntry>
      } else if (url.startsWith('/api/vocabulary?')) {
        body = { items: [entry(1, 'water'), entry(3, 'thank you', { category: 'PHRASE' })], page: 0, size: 24, totalItems: 2, totalPages: 1 }
      }
      return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
    }))

    render(<VocabularyPage />)
    expect(await screen.findByText('water')).toBeInTheDocument()
    expect(screen.getAllByText('Media not added')).toHaveLength(2)
    expect(screen.getByText('33.3%')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Search'), { target: { value: 'hel' } })
    expect(await screen.findByText('hello')).toBeInTheDocument()
    expect(screen.getByText('Verified ISL')).toBeInTheDocument()
    expect(urls.some((u) => u.startsWith('/api/vocabulary/search?') && u.includes('q=hel'))).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: 'Preview' }))
    expect(screen.getByLabelText('Sign video for hello')).toHaveAttribute('src', '/api/media/2')

    fireEvent.click(screen.getByLabelText('Phrases'))
    await waitFor(() => expect(urls.some((u) => u.includes('category=PHRASE'))).toBe(true))
  })

  it('shows an error when the API fails', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(JSON.stringify({ message: 'Database unavailable' }), { status: 503 }))))
    render(<VocabularyPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable')
  })
})
