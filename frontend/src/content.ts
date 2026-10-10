export const MAX_LENGTH = 500

export const EXAMPLES = [
  'I want to learn Indian Sign Language.',
  'What is your name?',
  'She went to school yesterday.',
  "I don't like cold tea.",
  'Thank you, my friend Zebra!',
]

export const PIPELINE = [
  {
    title: 'English input',
    short: 'A written sentence, up to 500 characters.',
    detail:
      'The translator accepts ordinary written English. Text is validated on the client and again on the server before processing.',
  },
  {
    title: 'NLP preprocessing',
    short: 'Sentences, tokens, POS tags and lemmas.',
    detail:
      'Apache OpenNLP splits sentences, tokenises, tags each token with a Universal Dependencies part of speech and lemmatises it, using the English EWT models. Contractions such as “don’t” are split into “do” and “not”.',
  },
  {
    title: 'ISL-oriented transformation',
    short: 'Explicit, testable reordering and filtering rules.',
    detail:
      'Seven named rules group known phrases, remove function words, use base forms, move time words first, place the verb after the object in simple clauses, keep negation after the verb and move question words last. Every change is reported.',
  },
  {
    title: 'Sign dictionary lookup',
    short: 'Words and phrases matched against the vocabulary.',
    detail:
      'Each unit is looked up by lemma, then surface form, then a simple singular form. Matches carry the entry’s media state: missing, available but unverified, or verified.',
  },
  {
    title: 'Fingerspelling fallback',
    short: 'Unknown words become ordered letters.',
    detail:
      'A word with no vocabulary entry is spelled letter by letter, for example ZEBRA becomes Z, E, B, R, A. Until ISL alphabet media is installed, each letter is a labelled placeholder.',
  },
  {
    title: 'Sequential playback',
    short: 'Play, pause, step and change speed.',
    detail:
      'The player shows videos, GIFs and still images by their own rules and shows placeholders for missing media, so the sequence order can be reviewed before media exists.',
  },
]
