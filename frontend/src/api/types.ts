export type SignStatus = 'NOT_IN_VOCABULARY' | 'MEDIA_MISSING' | 'AVAILABLE_UNVERIFIED' | 'VERIFIED'
export type SignCategory = 'WORD' | 'PHRASE' | 'LETTER' | 'DIGIT'
export type MediaType = 'VIDEO' | 'GIF' | 'IMAGE'
export type MappingSource = 'DICTIONARY' | 'FINGERSPELLING'

export interface TokenAnalysis {
  index: number
  sentence: number
  text: string
  lemma: string
  pos: string
  action: 'KEPT' | 'GROUPED' | 'DROPPED'
  rule?: string
  reason?: string
  gloss?: string
}

export interface SequenceUnit {
  index: number
  sentence: number
  gloss: string
  role: string
  mappingSource: MappingSource
  matchedBy?: string
  entryId?: number
  status: SignStatus
  firstPosition: number
  signCount: number
}

export interface RuleApplication {
  ruleId: string
  name: string
  sentence: number
  detail: string
  before?: string
  after?: string
}

export interface SignItem {
  position: number
  sentence: number
  unitIndex: number
  entryId?: number
  gloss: string
  representation: SignCategory
  mappingSource: MappingSource
  status: SignStatus
  verified: boolean
  playable: boolean
  placeholder: boolean
  mediaType?: MediaType
  mediaUrl?: string
  source?: string
  license?: string
  attribution?: string
  fingerspelledWord?: string
  letterIndex?: number
  letterCount?: number
  message?: string
}

export interface TranslationResult {
  originalText: string
  sentenceCount: number
  tokens: TokenAnalysis[]
  transformedSequence: string[]
  units: SequenceUnit[]
  rulesApplied: RuleApplication[]
  signs: SignItem[]
  summary: {
    tokenCount: number
    keptTokens: number
    droppedTokens: number
    units: number
    dictionaryMatches: number
    fingerspelledWords: number
    signItems: number
    playableItems: number
    verifiedItems: number
    placeholderItems: number
  }
  warnings: string[]
  processingTimeMs: number
}

export interface VocabularyEntry {
  id: number
  term: string
  normalizedTerm: string
  category: SignCategory
  verificationStatus: 'VERIFIED' | 'UNVERIFIED'
  status: SignStatus
  mediaRegistered: boolean
  mediaAvailable: boolean
  playable: boolean
  verified: boolean
  mediaType?: MediaType
  mediaUrl?: string
  source?: string
  license?: string
  attribution?: string
  notes?: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface VocabularyStats {
  totalEntries: number
  words: number
  phrases: number
  letters: number
  digits: number
  wordsAndPhrases: number
  targetWordsAndPhrases: number
  mediaRegistered: number
  mediaAvailable: number
  mediaMissing: number
  verified: number
  availableUnverified: number
  lettersWithMedia: number
  mediaCoveragePercent: number
}

export interface RuleInfo {
  id: string
  name: string
  description: string
}

export interface ApiErrorBody {
  status: number
  error: string
  message: string
  fieldErrors?: { field: string; message: string }[]
}
