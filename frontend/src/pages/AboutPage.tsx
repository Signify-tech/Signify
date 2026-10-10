export function AboutPage() {
  return (
    <>
      <header className="page-head page-head--lavender">
        <p className="eyebrow">About</p>
        <h1 tabIndex={-1}>An interpretable route from English to ISL.</h1>
        <p className="lede">
          Signify is a prototype assistive framework that converts written English into an ordered sequence of
          Indian Sign Language representations.
        </p>
      </header>

      <section className="section section--cream" aria-labelledby="motivation-title">
        <div className="split">
          <h2 id="motivation-title" className="section-title">Motivation</h2>
          <div className="prose">
            <p>
              Digital information is mostly written or spoken. For people whose first language is a sign language,
              that is a barrier, and ISL has its own grammar, syntax and vocabulary, so word-by-word substitution
              does not produce natural signing.
            </p>
            <p>
              Research on sign-language translation has moved towards large neural models, 3D avatars and
              generative pose synthesis. These need large annotated datasets, which are scarce for ISL, and they are
              hard to inspect and expensive to run.
            </p>
          </div>
        </div>
      </section>

      <section className="section section--charcoal" aria-labelledby="rationale-title">
        <div className="split">
          <h2 id="rationale-title" className="section-title">Design rationale</h2>
          <ul className="feature-list feature-list--light">
            <li>
              <h3>Lightweight</h3>
              <p>Small statistical NLP models and a dictionary lookup, rather than a generative model.</p>
            </li>
            <li>
              <h3>Interpretable</h3>
              <p>Every transformation is a named rule, and every decision is visible in the result.</p>
            </li>
            <li>
              <h3>Robust to unknown words</h3>
              <p>Fingerspelling keeps names and rare words in the sequence instead of dropping them.</p>
            </li>
            <li>
              <h3>Media-independent</h3>
              <p>Translation does not depend on media files, so the vocabulary and its media can grow separately.</p>
            </li>
          </ul>
        </div>
      </section>

      <section className="section section--cream" aria-labelledby="scope-title">
        <div className="split">
          <h2 id="scope-title" className="section-title">Scope</h2>
          <div className="prose">
            <p>
              In scope: written English input, tokenisation, part-of-speech tagging and lemmatisation, explicit
              ISL-oriented rules, vocabulary mapping, fingerspelling for unknown words, and sequential playback
              with play, pause, step and speed controls.
            </p>
            <p>
              Out of scope for this prototype: 3D avatars, speech input, sign recognition and large generative
              sign-language models.
            </p>
            <p>
              The project target is a vocabulary of at least 100 common words and phrases plus the alphabet. The
              vocabulary page shows the current counts and how many entries have media.
            </p>
          </div>
        </div>
      </section>

      <section className="section section--lavender" aria-labelledby="honest-title">
        <div className="split">
          <h2 id="honest-title" className="section-title">Limitations</h2>
          <ul className="plain-list">
            <li>
              No ISL media is bundled. The official ISLRTC dictionary resources require permission before reuse, so
              none have been copied here. Authentic media can be registered once permission is obtained.
            </li>
            <li>The rules simplify ISL grammar and have not yet been reviewed by fluent ISL signers.</li>
            <li>Non-manual features such as facial expression, used for questions and negation, are not represented.</li>
            <li>One English spelling maps to one vocabulary entry; word senses are not disambiguated.</li>
            <li>Complex sentences with several clauses keep their English order.</li>
            <li>No accuracy figures are claimed. Evaluation with ISL users is still to be done.</li>
          </ul>
        </div>
      </section>
    </>
  )
}
