import type { SignItem } from './api/types'

export const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5] as const

/** How long non-video items stay on screen at 1x speed, in milliseconds. */
export const HOLD_MS = { word: 1600, letter: 900, image: 1800, gif: 2600 } as const

export function holdDuration(item: SignItem): number {
  if (item.playable && item.mediaType === 'GIF') return HOLD_MS.gif
  if (item.playable && item.mediaType === 'IMAGE') return HOLD_MS.image
  return item.mappingSource === 'FINGERSPELLING' ? HOLD_MS.letter : HOLD_MS.word
}
