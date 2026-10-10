import { useState } from 'react'
import { ROUTES, type RouteId } from '../router'

export function SiteHeader({ current }: { current: RouteId }) {
  const [open, setOpen] = useState(false)

  return (
    <header className="site-header">
      <div className="nav-bar">
        <a className="brand" href="#/">Signify</a>
        <button
          type="button"
          className="nav-toggle"
          aria-expanded={open}
          aria-controls="primary-nav"
          onClick={() => setOpen((o) => !o)}
        >
          {open ? 'Close' : 'Menu'}
        </button>
        <nav id="primary-nav" aria-label="Primary" className={open ? 'nav nav--open' : 'nav'}>
          <ul>
            {ROUTES.map((r) => (
              <li key={r.id}>
                <a href={r.path} onClick={() => setOpen(false)} aria-current={r.id === current ? 'page' : undefined}>
                  {r.label}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <a className="nav-cta" href="#/translator">Try Translator</a>
      </div>
    </header>
  )
}
