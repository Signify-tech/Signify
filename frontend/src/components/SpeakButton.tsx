import { useEffect, useState } from 'react'

/** Reads the English text aloud with the browser's speech synthesis. Separate from the visual ISL output. */
export function SpeakButton({ text }: { text: string }) {
  const supported = typeof window !== 'undefined' && 'speechSynthesis' in window
  const [speaking, setSpeaking] = useState(false)

  useEffect(() => () => {
    if (supported) window.speechSynthesis.cancel()
  }, [supported])

  if (!supported || !text.trim()) return null

  const toggle = () => {
    if (speaking) {
      window.speechSynthesis.cancel()
      setSpeaking(false)
      return
    }
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = 'en-IN'
    utterance.onend = () => setSpeaking(false)
    utterance.onerror = () => setSpeaking(false)
    window.speechSynthesis.speak(utterance)
    setSpeaking(true)
  }

  return (
    <button type="button" className="btn btn--quiet" onClick={toggle} aria-pressed={speaking}>
      {speaking ? 'Stop English audio' : 'Read English aloud'}
    </button>
  )
}
