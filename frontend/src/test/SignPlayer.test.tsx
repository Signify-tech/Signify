import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SignPlayer } from '../components/SignPlayer'
import { HOLD_MS } from '../playback'
import { sign, zebraSigns } from './fixtures'

describe('SignPlayer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows a labelled placeholder with word and position when media is missing', () => {
    render(<SignPlayer items={[sign({ gloss: 'HELLO' }), sign({ position: 2, gloss: 'FRIEND' })]} />)
    expect(screen.getByText('Placeholder')).toBeInTheDocument()
    expect(screen.getAllByText('HELLO').length).toBeGreaterThan(0)
    expect(screen.getByText('Sign media not added yet.')).toBeInTheDocument()
    expect(screen.getByText('Position 1 of 2')).toBeInTheDocument()
    expect(screen.getByText(/No sign media is installed/)).toBeInTheDocument()
  })

  it('labels fingerspelled letters as placeholders', () => {
    render(<SignPlayer items={zebraSigns} />)
    expect(screen.getByText('Fingerspelling placeholder')).toBeInTheDocument()
    expect(screen.getByText(/Fingerspelling ZEBRA, letter 1 of 5/)).toBeInTheDocument()
  })

  it('displays and plays available fingerspelling images in order with the ISL warning', () => {
    const items = zebraSigns.map((s, i) => ({
      ...s,
      entryId: i + 1,
      mediaUrl: `/api/media/${i + 1}`,
      mediaType: 'IMAGE' as const,
      status: 'AVAILABLE_UNVERIFIED' as const,
      playable: true,
      placeholder: false,
    }))
    render(<SignPlayer items={items} />)
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    for (const item of items) {
      expect(screen.getByRole('img', { name: `Still image of the sign for ${item.gloss}` }))
        .toHaveAttribute('src', item.mediaUrl)
      expect(screen.getByText(`Fingerspelling ZEBRA, letter ${item.letterIndex} of 5`)).toBeInTheDocument()
      expect(screen.getByText(/Not yet verified as authentic ISL/)).toBeInTheDocument()
      expect(screen.queryByText('Fingerspelling placeholder')).toBeNull()
      act(() => { vi.advanceTimersByTime(HOLD_MS.image) })
    }
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
  })

  it('steps with next, previous and chips, and disables at the ends', () => {
    render(<SignPlayer items={zebraSigns} />)
    const prev = screen.getByRole('button', { name: 'Previous' })
    const next = screen.getByRole('button', { name: 'Next' })
    expect(prev).toBeDisabled()
    fireEvent.click(next)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
    fireEvent.click(screen.getByRole('button', { name: /Show sign 5: A/ }))
    expect(next).toBeDisabled()
    expect(screen.getByRole('button', { name: /Show sign 5: A/ })).toHaveAttribute('aria-current', 'step')
    fireEvent.click(prev)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '4')
  })

  it('plays through the sequence, honours speed, pauses at the end and restarts', () => {
    render(<SignPlayer items={zebraSigns} />)
    const progress = screen.getByRole('progressbar')
    fireEvent.change(screen.getByLabelText('Speed'), { target: { value: '0.5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    act(() => { vi.advanceTimersByTime(HOLD_MS.letter * 2 - 10) })
    expect(progress).toHaveAttribute('aria-valuenow', '1')
    act(() => { vi.advanceTimersByTime(20) })
    expect(progress).toHaveAttribute('aria-valuenow', '2')

    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    act(() => { vi.advanceTimersByTime(10_000) })
    expect(progress).toHaveAttribute('aria-valuenow', '2')

    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    for (let i = 0; i < 5; i++) act(() => { vi.advanceTimersByTime(HOLD_MS.letter * 2) })
    expect(progress).toHaveAttribute('aria-valuenow', '5')
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Restart' }))
    expect(progress).toHaveAttribute('aria-valuenow', '1')
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
  })

  it('supports arrow keys', () => {
    render(<SignPlayer items={zebraSigns} />)
    fireEvent.keyDown(screen.getByRole('region', { name: 'Sign sequence player' }), { key: 'ArrowRight' })
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
  })

  it('renders each media type with the right element', () => {
    const common = { playable: true, placeholder: false, status: 'AVAILABLE_UNVERIFIED' as const }
    const { container } = render(
      <SignPlayer
        items={[
          sign({ ...common, gloss: 'IMG', mediaType: 'IMAGE', mediaUrl: '/api/media/1' }),
          sign({ ...common, position: 2, gloss: 'ANIM', mediaType: 'GIF', mediaUrl: '/api/media/2' }),
          sign({ ...common, position: 3, gloss: 'VID', mediaType: 'VIDEO', mediaUrl: '/api/media/3', status: 'VERIFIED', verified: true }),
        ]}
      />,
    )
    expect(container.querySelector('video')).toBeNull()
    expect(screen.getByRole('img', { name: 'Still image of the sign for IMG' })).toHaveAttribute('src', '/api/media/1')
    expect(screen.getByText(/Not yet verified as authentic ISL/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByRole('img', { name: 'Animated sign for ANIM' })).toBeInTheDocument()
    expect(screen.getByText(/GIF animations keep moving/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    const video = container.querySelector('video')
    expect(video).not.toBeNull()
    expect(video).toHaveAttribute('src', '/api/media/3')
    expect(screen.queryByText(/Not yet verified/)).toBeNull()
  })

  it('falls back to a placeholder when media fails to load', () => {
    render(<SignPlayer items={[sign({ playable: true, mediaType: 'IMAGE', mediaUrl: '/api/media/9', gloss: 'X', status: 'VERIFIED' })]} />)
    fireEvent.error(screen.getByRole('img'))
    expect(screen.getByText('The media file could not be loaded.')).toBeInTheDocument()
  })

  const videoSign = (overrides = {}) => sign({
    playable: true, placeholder: false, mediaType: 'VIDEO', mediaUrl: '/api/media/9',
    gloss: 'HELLO', status: 'AVAILABLE_UNVERIFIED', ...overrides,
  })

  it('waits for video completion, respects pause and speed, then advances and finishes', () => {
    const { container } = render(<SignPlayer items={[videoSign(), sign({ position: 2 })]} />)
    const video = container.querySelector('video')!
    fireEvent.change(screen.getByLabelText('Speed'), { target: { value: '1.5' } })
    expect(video.playbackRate).toBe(1.5)
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    expect(video.play).toHaveBeenCalled()
    act(() => { vi.advanceTimersByTime(30_000) })
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1')
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    expect(video.pause).toHaveBeenCalled()
    fireEvent.ended(video)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1')
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    fireEvent.ended(video)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
    act(() => { vi.advanceTimersByTime(HOLD_MS.word / 1.5) })
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
  })

  it('restarts and replays a completed single video from the beginning', () => {
    const { container } = render(<SignPlayer items={[videoSign()]} />)
    const video = container.querySelector('video')!
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    video.currentTime = 5
    fireEvent.ended(video)
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    expect(video.currentTime).toBe(0)
    video.currentTime = 3
    fireEvent.click(screen.getByRole('button', { name: 'Restart' }))
    expect(video.currentTime).toBe(0)
    expect(screen.getByRole('button', { name: 'Pause' })).toBeInTheDocument()
  })

  it('resumes a paused final video without rewinding the sequence or the video', () => {
    const { container } = render(<SignPlayer items={[sign({}), videoSign({ position: 2 })]} />)
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    const video = container.querySelector('video')!
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    video.currentTime = 2
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
    expect(video.currentTime).toBe(2)
  })

  it('loads a fresh video for repeated signs and supports previous, next, and chips', () => {
    const { container } = render(<SignPlayer items={[videoSign(), videoSign({ position: 2 })]} />)
    const first = container.querySelector('video')!
    first.currentTime = 5
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    fireEvent.ended(first)
    const second = container.querySelector('video')!
    expect(second).not.toBe(first)
    expect(second.currentTime).toBe(0)
    fireEvent.click(screen.getByRole('button', { name: 'Previous' }))
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1')
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '2')
    fireEvent.click(screen.getByRole('button', { name: /Show sign 1:/ }))
    expect(screen.getByRole('button', { name: 'Play' })).toBeInTheDocument()
  })

  it('shows a missing-media badge and placeholder on video loading error', () => {
    const { container } = render(<SignPlayer items={[videoSign({ verified: true, status: 'VERIFIED' })]} />)
    fireEvent.error(container.querySelector('video')!)
    expect(screen.getByText('The media file could not be loaded.')).toBeInTheDocument()
    expect(screen.getByText('Media not added', { selector: '.badge' })).toBeInTheDocument()
    expect(container.querySelector('video')).toBeNull()
  })

  it.each(['switch', 'pause'])('ignores a late play rejection after %s', async (action) => {
    let reject!: (error: Error) => void
    vi.mocked(HTMLMediaElement.prototype.play).mockReturnValueOnce(new Promise((_, r) => { reject = r }))
    const image = sign({ position: 2, gloss: 'A', playable: true, mediaType: 'IMAGE', mediaUrl: '/api/media/1' })
    const { container } = render(<SignPlayer items={[videoSign(), image]} />)
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    fireEvent.click(screen.getByRole('button', { name: action === 'switch' ? 'Next' : 'Pause' }))
    await act(async () => { reject(new DOMException('Playback interrupted', 'AbortError')) })
    if (action === 'switch') {
      expect(screen.getByRole('img')).toBeInTheDocument()
      fireEvent.click(screen.getByRole('button', { name: 'Previous' }))
    }
    expect(container.querySelector('video')).not.toBeNull()
    expect(screen.queryByText('The media file could not be loaded.')).toBeNull()
  })

  it('falls back when the current video play promise rejects', async () => {
    vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValueOnce(new Error('Unsupported media'))
    render(<SignPlayer items={[videoSign()]} />)
    fireEvent.click(screen.getByRole('button', { name: 'Play' }))
    await act(async () => {})
    expect(screen.getByText('The media file could not be loaded.')).toBeInTheDocument()
  })
})
