import { describe, it, expect, beforeEach } from 'vitest';
import { DurationPipe } from './duration.pipe';

describe('DurationPipe', () => {
  let pipe: DurationPipe;

  beforeEach(() => {
    pipe = new DurationPipe();
  });

  it('renvoie le tiret pour null / undefined', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
  });

  it('affiche les secondes sous forme de millisecondes en dessous d’une heure', () => {
    expect(pipe.transform(0)).toBe('0 ms');
    expect(pipe.transform(0.234)).toBe('234 ms');
    expect(pipe.transform(42)).toBe('42000 ms');
  });

  it('arrondit les millisecondes', () => {
    // 0.2345 s -> 234.5 ms -> arrondi 235
    expect(pipe.transform(0.2345)).toBe('235 ms');
  });

  it('bascule en format h/m/s à partir d’une heure', () => {
    // 3725 s = 1h 02m 05s (exemple de la doc du pipe)
    expect(pipe.transform(3725)).toBe('1h 02m 05s');
  });

  it('remplit les minutes et secondes sur deux chiffres', () => {
    expect(pipe.transform(3600)).toBe('1h 00m 00s');
    expect(pipe.transform(3661)).toBe('1h 01m 01s');
  });

  it('gère les grandes durées (plusieurs heures)', () => {
    // 10h 00m 00s
    expect(pipe.transform(36000)).toBe('10h 00m 00s');
  });

  it('juste en dessous d’une heure reste en millisecondes', () => {
    expect(pipe.transform(3599)).toBe('3599000 ms');
  });
});
