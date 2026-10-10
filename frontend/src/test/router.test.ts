import { describe, expect, it } from 'vitest'
import { parseHash, translatorLink } from '../router'

describe('router', () => {
  it('parses known routes and falls back to home', () => {
    expect(parseHash('#/vocabulary').id).toBe('vocabulary')
    expect(parseHash('').id).toBe('home')
    expect(parseHash('#/nowhere').id).toBe('home')
  })

  it('round-trips translator example text', () => {
    const route = parseHash(translatorLink("I don't know & more?"))
    expect(route.id).toBe('translator')
    expect(route.params.get('text')).toBe("I don't know & more?")
  })
})
