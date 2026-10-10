import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { RuleInfo } from '../api/types'
import { PIPELINE } from '../content'
import { translatorLink } from '../router'

export function HowItWorksPage() {
  const [rules, setRules] = useState<RuleInfo[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    api.rules(controller.signal).then(setRules).catch(() => {
      if (!controller.signal.aborted) setError(true)
    })
    return () => controller.abort()
  }, [])

  return (
    <>
      <header className="page-head page-head--charcoal">
        <p className="eyebrow">How It Works</p>
        <h1 tabIndex={-1}>One sentence, six visible steps.</h1>
        <p className="lede">
          English Input → NLP Preprocessing → ISL-Oriented Transformation → Sign Dictionary Lookup → Fingerspelling
          Fallback → Sequential Playback.
        </p>
      </header>

      <section className="section section--cream" aria-label="Pipeline steps">
        <ol className="steps">
          {PIPELINE.map((step, i) => (
            <li key={step.title} className="step">
              <span className="step-num" aria-hidden="true">{String(i + 1).padStart(2, '0')}</span>
              <div>
                <h2>{step.title}</h2>
                <p>{step.detail}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="section section--lavender" aria-labelledby="rules-title">
        <div className="split">
          <div>
            <p className="eyebrow">Transformation rules</p>
            <h2 id="rules-title" className="section-title">The rules, as implemented</h2>
            <p className="muted">
              Loaded from the backend, so this list always matches the running code. The rules follow patterns
              commonly described for ISL; they are not a complete grammar and are applied conservatively.
            </p>
          </div>
          <div>
            {error && <p className="error-box" role="alert">The rule list could not be loaded from the server.</p>}
            {!rules && !error && <p className="muted" role="status">Loading rules…</p>}
            {rules && (
              <ol className="rule-list">
                {rules.map((r) => (
                  <li key={r.id}>
                    <p className="rule-id">{r.id}</p>
                    <h3>{r.name}</h3>
                    <p>{r.description}</p>
                  </li>
                ))}
              </ol>
            )}
          </div>
        </div>
      </section>

      <section className="section section--cream" aria-labelledby="example-title">
        <div className="split">
          <div>
            <p className="eyebrow">Worked example</p>
            <h2 id="example-title" className="section-title">“She went to school yesterday.”</h2>
          </div>
          <div>
            <ol className="plain-list numbered">
              <li>Tokens: She · went · to · school · yesterday · “.”, tagged PRON, VERB, ADP, NOUN, NOUN, PUNCT.</li>
              <li>R2 removes “to” and the full stop. R3 looks up “went” by its lemma, GO.</li>
              <li>R4 moves the time word first: YESTERDAY SHE GO SCHOOL.</li>
              <li>R6 puts the verb after the object: YESTERDAY SHE SCHOOL GO.</li>
              <li>Each gloss is looked up in the vocabulary; any unknown word would be fingerspelled.</li>
            </ol>
            <a className="btn btn--green" href={translatorLink('She went to school yesterday.')}>Run it in the translator</a>
          </div>
        </div>
      </section>
    </>
  )
}
