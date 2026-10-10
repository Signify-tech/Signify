import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { VocabularyStats } from '../api/types'
import { DotGrid } from '../components/DotGrid'
import { PIPELINE } from '../content'
import { translatorLink } from '../router'

export function HomePage() {
  const [stats, setStats] = useState<VocabularyStats | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    api.stats(controller.signal).then(setStats).catch(() => {})
    return () => controller.abort()
  }, [])

  return (
    <>
      <section className="hero" aria-labelledby="hero-title">
        <DotGrid rows={5} />
        <div className="hero-grid">
          <div className="hero-title-block">
            <h1 id="hero-title" tabIndex={-1}>Words into Signs. Communication Without Barriers.</h1>
          </div>
          <div className="hero-info">
            <p>
              Translate written English into an ordered sequence of Indian Sign Language representations through
              linguistic processing, sign mapping, and accessible playback.
            </p>
            <a className="hero-action" href="#/translator">
              <span>Open the translator</span>
              <span className="arrow" aria-hidden="true">↗</span>
            </a>
          </div>
          <div className="hero-stat">
            <p className="stat-figure">100+</p>
            <div>
              <p className="stat-label">Words and phrases: the project’s vocabulary target</p>
              <p className="stat-note">
                {stats
                  ? `${stats.wordsAndPhrases} registered so far, ${stats.mediaAvailable} with sign media.`
                  : 'Live counts load from the vocabulary service.'}
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="section section--cream intro" aria-labelledby="intro-title">
        <p className="eyebrow">Why Signify</p>
        <div className="intro-grid">
          <h2 id="intro-title" className="section-title">ISL has its own grammar. Word-by-word is not enough.</h2>
          <div className="intro-copy">
            <p>
              Most information is written, yet many Deaf people use Indian Sign Language as their first language.
              ISL orders ideas differently from English, so a literal word-for-word substitution rarely reads well.
            </p>
            <p>
              Signify is a lightweight, interpretable pipeline: it analyses the sentence, applies explicit
              ISL-oriented rules you can inspect, maps the result to a sign vocabulary and fingerspells what it does
              not know.
            </p>
          </div>
        </div>
      </section>

      <section className="section section--charcoal" aria-labelledby="pipeline-title">
        <div className="section-head">
          <p className="eyebrow">Pipeline</p>
          <h2 id="pipeline-title" className="section-title">Six steps from sentence to sequence</h2>
        </div>
        <ol className="pipeline">
          {PIPELINE.map((step, i) => (
            <li key={step.title}>
              <span className="pipeline-num">{String(i + 1).padStart(2, '0')}</span>
              <h3>{step.title}</h3>
              <p>{step.short}</p>
            </li>
          ))}
        </ol>
        <a className="text-link text-link--light" href="#/how-it-works">Read how each step works</a>
      </section>

      <section className="section section--cream" aria-labelledby="capabilities-title">
        <div className="split">
          <div>
            <p className="eyebrow">What it does</p>
            <h2 id="capabilities-title" className="section-title">Built to show its working</h2>
          </div>
          <ul className="feature-list">
            <li>
              <h3>Transparent processing</h3>
              <p>Every token shows its lemma, part of speech and whether it was kept, grouped or removed, and why.</p>
            </li>
            <li>
              <h3>Rules you can read</h3>
              <p>Reordering is done by seven named rules. Each change is listed with the order before and after.</p>
            </li>
            <li>
              <h3>Never a dead end</h3>
              <p>Words outside the vocabulary are fingerspelled, so names and rare words still appear in order.</p>
            </li>
            <li>
              <h3>Honest media states</h3>
              <p>The player separates missing words, missing media, unverified media and verified ISL signs.</p>
            </li>
          </ul>
        </div>
      </section>

      <section className="section section--lavender" aria-labelledby="limits-title">
        <div className="split">
          <div>
            <p className="eyebrow">Limitations</p>
            <h2 id="limits-title" className="section-title">What Signify does not do yet</h2>
          </div>
          <ul className="plain-list">
            <li>No ISL sign media ships with this installation. Placeholders are shown until authentic media is added.</li>
            <li>The rules are a simplification of ISL grammar and need review by fluent ISL signers.</li>
            <li>Facial expressions and other non-manual features, which carry meaning in ISL, are not represented.</li>
            <li>Word sense is not disambiguated: one English spelling maps to one vocabulary entry.</li>
          </ul>
        </div>
      </section>

      <section className="cta-band" aria-labelledby="cta-title">
        <h2 id="cta-title">Try a sentence.</h2>
        <ul className="cta-links">
          <li><a href={translatorLink('I want to learn Indian Sign Language.')}>I want to learn Indian Sign Language.</a></li>
          <li><a href={translatorLink('What is your name?')}>What is your name?</a></li>
          <li><a href={translatorLink('She went to school yesterday.')}>She went to school yesterday.</a></li>
        </ul>
      </section>
    </>
  )
}
