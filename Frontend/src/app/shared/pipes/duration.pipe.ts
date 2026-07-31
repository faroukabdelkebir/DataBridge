import { Pipe, PipeTransform } from '@angular/core';

/** 3725 -> '1h 02m 05s' ; 42 -> '42000 ms' ; 0.234 -> '234 ms' ; null -> '—' */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(seconds: number | null | undefined): string {
    if (seconds === null || seconds === undefined) return '—';
    // À partir d'une heure : format lisible h/m/s
    const h = Math.floor(seconds / 3600);
    if (h > 0) {
      const m = Math.floor((seconds / 60) % 60);
      const s = Math.floor(seconds % 60);
      return `${h}h ${pad(m)}m ${pad(s)}s`;
    }
    // Sinon : millisecondes (extractions rapides)
    return `${Math.round(seconds * 1000)} ms`;
  }
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}