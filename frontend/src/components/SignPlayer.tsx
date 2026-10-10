import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { mediaSrc } from '../api/client'
import type { SignItem } from '../api/types'
import { holdDuration, SPEEDS } from '../playback'
import { STATUS_LABEL } from '../status'
import { StatusBadge } from './StatusBadge'

interface Props {
  items: SignItem[]
}

export function SignPlayer({ items }: Props) {
  const [index, setIndex] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [speed, setSpeed] = useState<number>(1)
  const [failed, setFailed] = useState<Set<number>>(() => new Set())
  const videoRef = useRef<HTMLVideoElement>(null)

  const count = items.length
  const item = items[Math.min(index, count - 1)]
  const usesVideo = !!item && item.playable && item.mediaType === 'VIDEO' && !failed.has(item.position)

  const advance = useCallback(() => {
    if (index < count - 1) {
      setIndex(index + 1)
    } else {
      setPlaying(false)
      setCompleted(true)
    }
  }, [index, count])

  // Timed items: placeholders, images and GIFs.
  useEffect(() => {
    if (!playing || !item || usesVideo) return
    const timer = window.setTimeout(advance, holdDuration(item) / speed)
    return () => window.clearTimeout(timer)
  }, [playing, item, usesVideo, speed, advance])

  // Videos play to their end at the chosen rate.
  useEffect(() => {
    const video = videoRef.current
    if (!video || !usesVideo) return
    let cancelled = false
    video.playbackRate = speed
    if (playing) {
      video.play().catch(() => {
        if (!cancelled) setFailed((f) => new Set(f).add(item.position))
      })
    } else {
      video.pause()
    }
    return () => { cancelled = true }
  }, [playing, usesVideo, speed, item])

  if (!item) return null

  const go = (i: number) => {
    setCompleted(false)
    setIndex(Math.max(0, Math.min(count - 1, i)))
  }

  const togglePlay = () => {
    if (!playing && completed) {
      setIndex(0)
      setCompleted(false)
      if (videoRef.current) videoRef.current.currentTime = 0
    }
    setPlaying((p) => !p)
  }

  const restart = () => {
    setIndex(0)
    setCompleted(false)
    setPlaying(true)
    if (videoRef.current) videoRef.current.currentTime = 0
  }

  const onKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    const tag = (e.target as HTMLElement).tagName
    if (tag === 'SELECT' || tag === 'INPUT' || tag === 'TEXTAREA') return
    if (e.key === 'ArrowRight') {
      e.preventDefault()
      go(index + 1)
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      go(index - 1)
    }
  }

  const playableCount = items.filter((i) => i.playable).length

  return (
    <section className="player" aria-label="Sign sequence player" onKeyDown={onKeyDown}>
      <div className="player-stage" data-kind={item.playable && !failed.has(item.position) ? item.mediaType : 'PLACEHOLDER'}>
        <Stage
          item={item}
          failed={failed.has(item.position)}
          videoRef={videoRef}
          onVideoEnded={() => { if (playing) advance() }}
          onMediaError={() => setFailed((f) => new Set(f).add(item.position))}
          count={count}
        />
      </div>

      <div className="player-meta">
        <p className="player-position">
          <span className="sr-only">Sign </span>
          {item.position} <span aria-hidden="true">/</span><span className="sr-only">of</span> {count}
        </p>
        <div className="player-meta-text">
          <p className="player-gloss">{item.gloss}</p>
          <p className="player-source">
            {item.mappingSource === 'FINGERSPELLING'
              ? `Fingerspelling ${item.fingerspelledWord}, letter ${item.letterIndex} of ${item.letterCount}`
              : `Dictionary ${item.representation === 'PHRASE' ? 'phrase' : 'word'}`}
          </p>
        </div>
        <StatusBadge status={failed.has(item.position) ? 'MEDIA_MISSING' : item.status} />
      </div>

      <div
        className="player-progress"
        role="progressbar"
        aria-label="Sequence progress"
        aria-valuemin={1}
        aria-valuemax={count}
        aria-valuenow={item.position}
        aria-valuetext={`Sign ${item.position} of ${count}`}
      >
        <span style={{ width: `${(item.position / count) * 100}%` }} />
      </div>

      <div className="player-controls">
        <button type="button" className="ctrl" onClick={restart}>Restart</button>
        <button type="button" className="ctrl" onClick={() => go(index - 1)} disabled={index === 0}>
          Previous
        </button>
        <button type="button" className="ctrl ctrl--primary" onClick={togglePlay} aria-pressed={playing}>
          {playing ? 'Pause' : 'Play'}
        </button>
        <button type="button" className="ctrl" onClick={() => go(index + 1)} disabled={index === count - 1}>
          Next
        </button>
        <label className="speed">
          <span>Speed</span>
          <select value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>
            {SPEEDS.map((s) => (
              <option key={s} value={s}>{s}×</option>
            ))}
          </select>
        </label>
      </div>

      <p className="sr-only" aria-live="polite">
        {`Sign ${item.position} of ${count}: ${item.gloss}. ${STATUS_LABEL[failed.has(item.position) ? 'MEDIA_MISSING' : item.status]}.`}
      </p>

      {playableCount === 0 && (
        <p className="player-note">
          No sign media is installed for this sequence. Each step shows a labelled placeholder so the order and
          timing can still be reviewed.
        </p>
      )}

      <ol className="player-strip" aria-label="Signs in this sequence">
        {items.map((s, i) => (
          <li key={s.position}>
            <button
              type="button"
              className={`chip chip--${s.mappingSource === 'FINGERSPELLING' ? 'letter' : 'word'}${s.playable ? ' chip--media' : ''}`}
              aria-current={i === index ? 'step' : undefined}
              onClick={() => {
                setPlaying(false)
                go(i)
              }}
              aria-label={`Show sign ${s.position}: ${s.gloss}, ${STATUS_LABEL[s.status]}`}
            >
              <span className="chip-num" aria-hidden="true">{s.position}</span>
              {s.gloss}
            </button>
          </li>
        ))}
      </ol>
    </section>
  )
}

interface StageProps {
  item: SignItem
  count: number
  failed: boolean
  videoRef: React.RefObject<HTMLVideoElement | null>
  onVideoEnded: () => void
  onMediaError: () => void
}

function Stage({ item, count, failed, videoRef, onVideoEnded, onMediaError }: StageProps) {
  if (item.playable && item.mediaUrl && !failed) {
    const src = mediaSrc(item.mediaUrl)
    const credit = [item.source, item.license, item.attribution].filter(Boolean).join(' · ')
    return (
      <figure className="stage-media">
        {item.mediaType === 'VIDEO' && (
          <video
            key={`${item.position}:${src}`}
            ref={videoRef}
            src={src}
            muted
            playsInline
            preload="auto"
            onEnded={onVideoEnded}
            onError={onMediaError}
            aria-label={`Sign video for ${item.gloss}`}
          />
        )}
        {item.mediaType === 'GIF' && (
          <img key={src} src={src} alt={`Animated sign for ${item.gloss}`} onError={onMediaError} />
        )}
        {item.mediaType === 'IMAGE' && (
          <img key={src} src={src} alt={`Still image of the sign for ${item.gloss}`} onError={onMediaError} />
        )}
        <figcaption>
          {!item.verified && <strong>Not yet verified as authentic ISL. </strong>}
          {item.mediaType === 'GIF' && 'GIF animations keep moving while the sequence is paused. '}
          {credit}
        </figcaption>
      </figure>
    )
  }

  const letter = item.mappingSource === 'FINGERSPELLING'
  return (
    <div className={`placeholder${letter ? ' placeholder--letter' : ''}`}>
      <p className="placeholder-tag">{letter ? 'Fingerspelling placeholder' : 'Placeholder'}</p>
      <p className="placeholder-gloss" lang="en">{item.gloss}</p>
      <p className="placeholder-msg">
        {failed ? 'The media file could not be loaded.' : item.message ?? 'Sign media not added yet.'}
      </p>
      <p className="placeholder-pos">
        Position {item.position} of {count}
        {letter && ` · ${item.fingerspelledWord} (${item.letterIndex}/${item.letterCount})`}
      </p>
    </div>
  )
}
