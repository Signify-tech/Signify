export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <p className="footer-brand">Signify</p>
        <p>
          An NLP-based prototype that turns written English into an ordered sequence of Indian Sign Language
          representations. Sequences are rule-based approximations and should be reviewed by fluent ISL signers.
        </p>
        <ul className="footer-links">
          <li><a href="#/translator">Translator</a></li>
          <li><a href="#/vocabulary">Vocabulary</a></li>
          <li><a href="#/how-it-works">How It Works</a></li>
          <li><a href="#/about">About</a></li>
        </ul>
      </div>
    </footer>
  )
}
