import type { TranslationResult } from '../api/types'
import { StatusBadge } from './StatusBadge'

const ACTION_LABEL = { KEPT: 'Kept', GROUPED: 'Phrase', DROPPED: 'Removed' } as const

export function TranslationResults({ result }: { result: TranslationResult }) {
  const { summary } = result
  return (
    <div className="results">
      <section className="result-block result-block--source" aria-labelledby="original-heading">
        <h3 id="original-heading" className="eyebrow">Original text</h3>
        <p className="original-text">{result.originalText}</p>
      </section>

      <section className="result-block" aria-labelledby="sequence-heading">
        <h3 id="sequence-heading" className="eyebrow">ISL-oriented sequence</h3>
        <ol className="gloss-row">
          {result.units.map((u) => (
            <li key={u.index} className={`gloss gloss--${u.mappingSource.toLowerCase()}`}>
              <span className="gloss-word">{u.gloss}</span>
              <span className="gloss-how">
                {u.mappingSource === 'FINGERSPELLING' ? `Fingerspelled · ${u.signCount} letters` : `Dictionary · ${u.matchedBy}`}
              </span>
            </li>
          ))}
        </ol>
        {result.units.length === 0 && <p className="muted">Nothing to sign.</p>}
      </section>

      <dl className="summary">
        <div><dt>Dictionary matches</dt><dd>{summary.dictionaryMatches}</dd></div>
        <div><dt>Fingerspelled words</dt><dd>{summary.fingerspelledWords}</dd></div>
        <div><dt>Sign items</dt><dd>{summary.signItems}</dd></div>
        <div><dt>With playable media</dt><dd>{summary.playableItems}</dd></div>
        <div><dt>Processing time</dt><dd>{result.processingTimeMs.toFixed(1)} ms</dd></div>
      </dl>

      {result.warnings.length > 0 && (
        <section className="notices" aria-labelledby="notices-heading">
          <h3 id="notices-heading" className="eyebrow">Notes</h3>
          <ul>
            {result.warnings.map((w) => <li key={w}>{w}</li>)}
          </ul>
        </section>
      )}

      <section className="result-block" aria-labelledby="signs-heading">
        <h3 id="signs-heading" className="eyebrow">Sign items</h3>
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Sign</th>
                <th scope="col">Type</th>
                <th scope="col">Mapping</th>
                <th scope="col">Media</th>
              </tr>
            </thead>
            <tbody>
              {result.signs.map((s) => (
                <tr key={s.position}>
                  <td className="num">{s.position}</td>
                  <td className="strong">{s.gloss}</td>
                  <td>{s.representation.toLowerCase()}</td>
                  <td>
                    {s.mappingSource === 'FINGERSPELLING'
                      ? `Fingerspelling (${s.fingerspelledWord})`
                      : 'Dictionary'}
                  </td>
                  <td><StatusBadge status={s.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <details className="details">
        <summary>Processing details: tokens and rules</summary>
        <div className="details-body">
          <h4 className="eyebrow">Tokens ({summary.tokenCount}: {summary.keptTokens} kept, {summary.droppedTokens} removed)</h4>
          <div className="table-wrap">
            <table className="data-table data-table--compact">
              <thead>
                <tr>
                  <th scope="col">Token</th>
                  <th scope="col">Lemma</th>
                  <th scope="col">POS</th>
                  <th scope="col">Action</th>
                  <th scope="col">Reason</th>
                </tr>
              </thead>
              <tbody>
                {result.tokens.map((t) => (
                  <tr key={t.index} className={t.action === 'DROPPED' ? 'is-dropped' : undefined}>
                    <td className="strong">{t.text}</td>
                    <td>{t.lemma}</td>
                    <td><abbr title="Universal Dependencies part-of-speech tag">{t.pos}</abbr></td>
                    <td>{ACTION_LABEL[t.action]}</td>
                    <td>{t.reason}{t.rule ? ` (${t.rule})` : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h4 className="eyebrow">Rules applied</h4>
          {result.rulesApplied.length === 0 ? (
            <p className="muted">No rule changed this input.</p>
          ) : (
            <ol className="rule-log">
              {result.rulesApplied.map((r, i) => (
                <li key={i}>
                  <p><strong>{r.ruleId}</strong> {r.name}{result.sentenceCount > 1 ? ` (sentence ${r.sentence + 1})` : ''}</p>
                  <p className="muted">{r.detail}</p>
                  {r.before && (
                    <p className="rule-diff">
                      <span>{r.before}</span> <span aria-hidden="true">→</span><span className="sr-only">becomes</span> <span>{r.after}</span>
                    </p>
                  )}
                </li>
              ))}
            </ol>
          )}
        </div>
      </details>
    </div>
  )
}
