import type { SignStatus } from './api/types'

export const STATUS_LABEL: Record<SignStatus, string> = {
  NOT_IN_VOCABULARY: 'Not in vocabulary',
  MEDIA_MISSING: 'Media not added',
  AVAILABLE_UNVERIFIED: 'Unverified media',
  VERIFIED: 'Verified ISL',
}

export const STATUS_HELP: Record<SignStatus, string> = {
  NOT_IN_VOCABULARY: 'No vocabulary entry exists for this word or character.',
  MEDIA_MISSING: 'The vocabulary entry exists, but no sign media file has been added.',
  AVAILABLE_UNVERIFIED: 'A media file exists but has not been verified as authentic ISL.',
  VERIFIED: 'A media file exists and is marked as a verified ISL sign.',
}
