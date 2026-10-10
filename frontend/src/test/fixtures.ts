import type { SignItem, TranslationResult } from '../api/types'

export function sign(overrides: Partial<SignItem>): SignItem {
  return {
    position: 1,
    sentence: 0,
    unitIndex: 0,
    gloss: 'WORD',
    representation: 'WORD',
    mappingSource: 'DICTIONARY',
    status: 'MEDIA_MISSING',
    verified: false,
    playable: false,
    placeholder: true,
    message: 'Sign media not added yet.',
    ...overrides,
  }
}

export const zebraSigns: SignItem[] = ['Z', 'E', 'B', 'R', 'A'].map((l, i) =>
  sign({
    position: i + 1,
    gloss: l,
    representation: 'LETTER',
    mappingSource: 'FINGERSPELLING',
    fingerspelledWord: 'ZEBRA',
    letterIndex: i + 1,
    letterCount: 5,
    message: 'Fingerspelling placeholder. ISL alphabet media not added yet.',
  }),
)

export const result: TranslationResult = {
  originalText: 'I like zebra.',
  sentenceCount: 1,
  tokens: [
    { index: 0, sentence: 0, text: 'I', lemma: 'i', pos: 'PRON', action: 'KEPT', reason: 'Content word kept', gloss: 'I' },
    { index: 1, sentence: 0, text: 'like', lemma: 'like', pos: 'VERB', action: 'KEPT', reason: 'Content word kept', gloss: 'LIKE' },
    { index: 2, sentence: 0, text: 'zebra', lemma: 'zebra', pos: 'NOUN', action: 'KEPT', reason: 'Content word kept', gloss: 'ZEBRA' },
    { index: 3, sentence: 0, text: '.', lemma: '.', pos: 'PUNCT', action: 'DROPPED', rule: 'R2_FILTER', reason: 'Punctuation' },
  ],
  transformedSequence: ['I', 'ZEBRA', 'LIKE'],
  units: [
    { index: 0, sentence: 0, gloss: 'I', role: 'OTHER', mappingSource: 'DICTIONARY', matchedBy: 'lemma', entryId: 1, status: 'MEDIA_MISSING', firstPosition: 1, signCount: 1 },
    { index: 1, sentence: 0, gloss: 'ZEBRA', role: 'OTHER', mappingSource: 'FINGERSPELLING', status: 'NOT_IN_VOCABULARY', firstPosition: 2, signCount: 5 },
    { index: 2, sentence: 0, gloss: 'LIKE', role: 'VERB', mappingSource: 'DICTIONARY', matchedBy: 'lemma', entryId: 2, status: 'MEDIA_MISSING', firstPosition: 7, signCount: 1 },
  ],
  rulesApplied: [
    { ruleId: 'R6_VERB_FINAL', name: 'Verb after object', sentence: 0, detail: 'Verb group moved after the object', before: 'I LIKE ZEBRA', after: 'I ZEBRA LIKE' },
  ],
  signs: [
    sign({ position: 1, gloss: 'I', entryId: 1 }),
    ...zebraSigns.map((s) => ({ ...s, position: s.position + 1, unitIndex: 1 })),
    sign({ position: 7, gloss: 'LIKE', entryId: 2, unitIndex: 2 }),
  ],
  summary: {
    tokenCount: 4, keptTokens: 3, droppedTokens: 1, units: 3, dictionaryMatches: 2, fingerspelledWords: 1,
    signItems: 7, playableItems: 0, verifiedItems: 0, placeholderItems: 7,
  },
  warnings: ['Not in the vocabulary, fingerspelled letter by letter: ZEBRA.'],
  processingTimeMs: 4.2,
}
