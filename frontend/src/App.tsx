import { useEffect, useRef } from 'react'
import { SiteFooter } from './components/SiteFooter'
import { SiteHeader } from './components/SiteHeader'
import { AboutPage } from './pages/AboutPage'
import { HomePage } from './pages/HomePage'
import { HowItWorksPage } from './pages/HowItWorksPage'
import { TranslatorPage } from './pages/TranslatorPage'
import { VocabularyPage } from './pages/VocabularyPage'
import { ROUTES, useRoute } from './router'

export default function App() {
  const route = useRoute()
  const mainRef = useRef<HTMLElement>(null)
  const firstRender = useRef(true)

  useEffect(() => {
    const label = ROUTES.find((r) => r.id === route.id)?.label ?? 'Home'
    document.title = route.id === 'home' ? 'Signify: English to Indian Sign Language' : `${label} · Signify`
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    window.scrollTo(0, 0)
    mainRef.current?.querySelector<HTMLElement>('h1')?.focus()
  }, [route.id])

  return (
    <>
      <a className="skip-link" href="#main" onClick={(e) => {
        e.preventDefault()
        mainRef.current?.focus()
      }}>Skip to content</a>
      <SiteHeader current={route.id} />
      <main id="main" ref={mainRef} tabIndex={-1}>
        {route.id === 'home' && <HomePage />}
        {route.id === 'how-it-works' && <HowItWorksPage />}
        {route.id === 'translator' && <TranslatorPage key={route.params.get('text') ?? ''} initialText={route.params.get('text') ?? ''} />}
        {route.id === 'vocabulary' && <VocabularyPage />}
        {route.id === 'about' && <AboutPage />}
      </main>
      <SiteFooter />
    </>
  )
}
