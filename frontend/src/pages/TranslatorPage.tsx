import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { TranslationResult } from '../api/types'
import { SignPlayer } from '../components/SignPlayer'
import { SpeakButton } from '../components/SpeakButton'
import { TranslationResults } from '../components/TranslationResults'
import { EXAMPLES, MAX_LENGTH } from '../content'

type State =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'done'; result: TranslationResult; id: number }

export function TranslatorPage({ initialText = '' }: { initialText?: string }) {
  const [text, setText] = useState(initialText.slice(0, MAX_LENGTH))
  const [state, setState] = useState<State>({ kind: 'idle' })
  const [touched, setTouched] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const runId = useRef(0)

  useEffect(() => () => {
    runId.current += 1
    abortRef.current?.abort()
  }, [])

  const changeText = (value: string) => {
    runId.current += 1
    abortRef.current?.abort()
    setText(value)
    setState({ kind: 'idle' })
  }

  const trimmed = text.trim()
  const tooLong = text.length > MAX_LENGTH
  const validation = !trimmed ? 'Enter some English text to translate.' : tooLong ? `Use ${MAX_LENGTH} characters or fewer.` : null

  const submit = async (e?: FormEvent) => {
    e?.preventDefault()
    setTouched(true)
    if (validation) return
    abortRef.current?.abort()
    const controller = new AbortController()
    const id = ++runId.current
    abortRef.current = controller
    setState({ kind: 'loading' })
    try {
      const result = await api.translate(trimmed, controller.signal)
      if (controller.signal.aborted || id !== runId.current) return
      setState({ kind: 'done', result, id })
    } catch (err) {
      if (controller.signal.aborted || id !== runId.current) return
      setState({ kind: 'error', message: err instanceof Error ? err.message : 'Translation failed.' })
    }
  }

  const reset = () => {
    changeText('')
    setTouched(false)
    setState({ kind: 'idle' })
  }

  const showError = touched && validation

  return (
    <>
      <header className="page-head page-head--terracotta">
        <p className="eyebrow">Translator</p>
        <h1 tabIndex={-1}>English in, ISL sequence out.</h1>
        <p className="lede">
          Type a sentence. Signify analyses it, applies its ISL-oriented rules, looks each unit up in the sign
          vocabulary and fingerspells anything it does not know.
        </p>
      </header>

      <div className="translator">
        <form className="translator-form" onSubmit={submit} noValidate>
          <label htmlFor="english-input" className="field-label">English text</label>
          <textarea
            id="english-input"
            value={text}
            rows={6}
            onChange={(e) => changeText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                e.preventDefault()
                submit()
              }
            }}
            aria-invalid={showError ? true : undefined}
            aria-describedby="input-help input-count input-error"
            placeholder="For example: I want to learn Indian Sign Language."
          />
          <div className="field-foot">
            <p id="input-help" className="muted">Up to {MAX_LENGTH} characters. Ctrl or Cmd + Enter translates.</p>
            <p id="input-count" className={tooLong ? 'count count--over' : 'count'} aria-live="polite">
              {text.length} / {MAX_LENGTH}
            </p>
          </div>
          <p id="input-error" className="field-error" role={showError ? 'alert' : undefined}>
            {showError ? validation : ''}
          </p>

          <div className="form-actions">
            <button type="submit" className="btn btn--green" disabled={state.kind === 'loading'}>
              {state.kind === 'loading' ? 'Translating…' : 'Translate'}
            </button>
            <button type="button" className="btn btn--quiet" onClick={reset}>Clear</button>
            <SpeakButton text={trimmed} />
          </div>

          <fieldset className="examples">
            <legend className="eyebrow">Try an example</legend>
            <ul>
              {EXAMPLES.map((ex) => (
                <li key={ex}>
                  <button type="button" className="example" onClick={() => { changeText(ex); setTouched(false) }}>
                    {ex}
                  </button>
                </li>
              ))}
            </ul>
          </fieldset>
        </form>

        <div className="translator-output" aria-busy={state.kind === 'loading'}>
          <p className="sr-only" role="status">
            {state.kind === 'done' ? `Translation ready: ${state.result.signs.length} sign items.` : ''}
          </p>
          {state.kind === 'idle' && (
            <div className="empty-state">
              <p className="empty-title">The sign sequence appears here.</p>
              <p className="muted">
                Available sign media plays in sequence. Signs without media appear as labelled placeholders.
              </p>
            </div>
          )}
          {state.kind === 'loading' && <p className="loading" role="status">Analysing sentence…</p>}
          {state.kind === 'error' && (
            <div className="error-box" role="alert">
              <p className="strong">Translation failed</p>
              <p>{state.message}</p>
              <button type="button" className="btn btn--quiet" onClick={() => submit()}>Try again</button>
            </div>
          )}
          {state.kind === 'done' && state.result.signs.length > 0 && (
            <SignPlayer key={state.id} items={state.result.signs} />
          )}
          {state.kind === 'done' && state.result.signs.length === 0 && (
            <div className="empty-state">
              <p className="empty-title">No signs were produced.</p>
              <p className="muted">The input had no words left to sign after processing.</p>
            </div>
          )}
        </div>
      </div>

      {state.kind === 'done' && (
        <section className="section section--cream results-section" aria-labelledby="results-heading">
          <h2 id="results-heading" className="section-title">Translation result</h2>
          <TranslationResults result={state.result} />
        </section>
      )}
    </>
  )
}
