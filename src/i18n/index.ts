import { de, type Woerterbuch } from './de.ts';
import { en } from './en.ts';
import { useEinstellungen, type Sprache } from '../wallet/einstellungen.ts';

export type { Woerterbuch };

export function sprachCode(s: Sprache): 'de' | 'en' {
  if (s !== 'system') return s;
  return (navigator.language || 'en').toLowerCase().startsWith('de') ? 'de' : 'en';
}

export function woerterbuch(s: Sprache): Woerterbuch {
  return sprachCode(s) === 'de' ? de : en;
}

export function useT(): Woerterbuch {
  return woerterbuch(useEinstellungen().sprache);
}
