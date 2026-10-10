import { useEffect, useState } from 'react'
import { api, mediaSrc } from '../api/client'
import type { Page, SignCategory, VocabularyEntry, VocabularyStats } from '../api/types'
import { StatusBadge } from '../components/StatusBadge'
import { useDebounced } from '../hooks'

const CATEGORIES: { value: SignCategory | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'WORD', label: 'Words' },
  { value: 'PHRASE', label: 'Phrases' },
  { value: 'LETTER', label: 'Letters' },
  { value: 'DIGIT', label: 'Digits' },
]

const PAGE_SIZE = 24

export function VocabularyPage() {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<SignCategory | ''>('')
  const [page, setPage] = useState(0)
  const [loaded, setLoaded] = useState<{ key: string; data: Page<VocabularyEntry> | null; error: string | null }>({
    key: '',
    data: null,
    error: null,
  })
  const [stats, setStats] = useState<VocabularyStats | null>(null)
  const q = useDebounced(query, 250)
  const requestKey = JSON.stringify([q, category, page])
  const loading = loaded.key !== requestKey
  const { data, error } = loaded

  useEffect(() => {
    const controller = new AbortController()
    api.stats(controller.signal).then(setStats).catch(() => {})
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    api
      .vocabulary({ q, category, page, size: PAGE_SIZE }, controller.signal)
      .then((d) => setLoaded({ key: requestKey, data: d, error: null }))
      .catch((e: Error) => {
        if (!controller.signal.aborted) setLoaded((prev) => ({ key: requestKey, data: prev.data, error: e.message }))
      })
    return () => controller.abort()
  }, [q, category, page, requestKey])

  return (
    <>
      <header className="page-head page-head--green">
        <p className="eyebrow">Vocabulary</p>
        <h1 tabIndex={-1}>The sign dictionary, as it stands.</h1>
        <p className="lede">
          Every entry the translator can match, straight from the database, with the honest state of its media.
          Entries without media still count as dictionary matches; the player shows a placeholder for them.
        </p>
      </header>

      {stats && (
        <section className="stats-strip" aria-label="Vocabulary statistics">
          <dl>
            <div><dt>Words and phrases</dt><dd>{stats.wordsAndPhrases}</dd></div>
            <div><dt>Letters and digits</dt><dd>{stats.letters + stats.digits}</dd></div>
            <div><dt>With playable media</dt><dd>{stats.mediaAvailable}</dd></div>
            <div><dt>Verified ISL media</dt><dd>{stats.verified}</dd></div>
            <div><dt>Media coverage</dt><dd>{stats.mediaCoveragePercent}%</dd></div>
          </dl>
        </section>
      )}

      <section className="section section--cream" aria-labelledby="browse-heading">
        <h2 id="browse-heading" className="sr-only">Browse entries</h2>
        <div className="vocab-tools">
          <div className="search">
            <label htmlFor="vocab-search" className="field-label">Search</label>
            <input
              id="vocab-search"
              type="search"
              value={query}
              maxLength={120}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(0)
              }}
              placeholder="Word or phrase"
              autoComplete="off"
            />
          </div>
          <fieldset className="segmented">
            <legend className="field-label">Category</legend>
            {CATEGORIES.map((c) => (
              <label key={c.value || 'all'} className={category === c.value ? 'is-active' : undefined}>
                <input
                  type="radio"
                  name="category"
                  value={c.value}
                  checked={category === c.value}
                  onChange={() => {
                    setCategory(c.value)
                    setPage(0)
                  }}
                />
                {c.label}
              </label>
            ))}
          </fieldset>
        </div>

        <p className="muted result-count" aria-live="polite">
          {loading ? 'Loading…' : data ? `${data.totalItems} ${data.totalItems === 1 ? 'entry' : 'entries'}` : ''}
        </p>

        {error && <div className="error-box" role="alert"><p>{error}</p></div>}

        {data && data.items.length > 0 && (
          <ul className="vocab-list">
            {data.items.map((entry) => <VocabularyRow key={entry.id} entry={entry} />)}
          </ul>
        )}
        {data && data.items.length === 0 && !loading && (
          <p className="empty-title">No entries match “{q}”. Unknown words are fingerspelled by the translator.</p>
        )}

        {data && data.totalPages > 1 && (
          <nav className="pager" aria-label="Vocabulary pages">
            <button type="button" className="btn btn--quiet" disabled={page === 0} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <span>Page {page + 1} of {data.totalPages}</span>
            <button
              type="button"
              className="btn btn--quiet"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </nav>
        )}
      </section>
    </>
  )
}

function VocabularyRow({ entry }: { entry: VocabularyEntry }) {
  const [preview, setPreview] = useState(false)
  return (
    <li className="vocab-item">
      <div className="vocab-main">
        <p className="vocab-term">{entry.term}</p>
        <p className="vocab-cat">{entry.category.toLowerCase()}</p>
      </div>
      <div className="vocab-status">
        <StatusBadge status={entry.status} />
        <p className="muted small">
          {entry.playable ? `Playable ${entry.mediaType?.toLowerCase()}` : entry.mediaRegistered ? 'File registered, not found' : 'No media registered'}
        </p>
      </div>
      {entry.playable && entry.mediaUrl && (
        <div className="vocab-preview">
          <button type="button" className="btn btn--quiet" aria-expanded={preview} onClick={() => setPreview(!preview)}>
            {preview ? 'Hide preview' : 'Preview'}
          </button>
          {preview && (entry.mediaType === 'VIDEO'
            ? <video src={mediaSrc(entry.mediaUrl)} controls muted playsInline aria-label={`Sign video for ${entry.term}`} />
            : <img src={mediaSrc(entry.mediaUrl)} alt={`Sign for ${entry.term}`} />)}
          {preview && entry.attribution && <p className="muted small">{entry.attribution}</p>}
        </div>
      )}
    </li>
  )
}
