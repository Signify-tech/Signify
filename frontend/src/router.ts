import { useEffect, useState } from 'react'

export type RouteId = 'home' | 'how-it-works' | 'translator' | 'vocabulary' | 'about'

export const ROUTES: { id: RouteId; path: string; label: string }[] = [
  { id: 'home', path: '#/', label: 'Home' },
  { id: 'how-it-works', path: '#/how-it-works', label: 'How It Works' },
  { id: 'translator', path: '#/translator', label: 'Translator' },
  { id: 'vocabulary', path: '#/vocabulary', label: 'Vocabulary' },
  { id: 'about', path: '#/about', label: 'About' },
]

export interface Route {
  id: RouteId
  params: URLSearchParams
}

export function parseHash(hash: string): Route {
  const [path, query = ''] = hash.replace(/^#/, '').split('?')
  const match = ROUTES.find((r) => r.path === `#${path || '/'}` )
  return { id: match?.id ?? 'home', params: new URLSearchParams(query) }
}

export function translatorLink(text: string): string {
  return `#/translator?${new URLSearchParams({ text })}`
}

/** Minimal hash router: keeps the app deployable as static files without server rewrites. */
export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseHash(window.location.hash))
  useEffect(() => {
    const onChange = () => setRoute(parseHash(window.location.hash))
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}
