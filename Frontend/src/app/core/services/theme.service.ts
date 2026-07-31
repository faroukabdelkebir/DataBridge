import { Injectable, effect, signal } from '@angular/core';

const STORAGE_KEY = 'bi_theme';
const DARK_CLASS = 'app-dark'; // doit correspondre à darkModeSelector dans app.config.ts

@Injectable({ providedIn: 'root' })
export class ThemeService {
  /** true = thème sombre actif */
  readonly darkMode = signal<boolean>(this.resolveInitial());

  constructor() {
    // À chaque changement : applique la classe sur <html> + persiste le choix
    effect(() => {
      const dark = this.darkMode();
      document.documentElement.classList.toggle(DARK_CLASS, dark);
      localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
    });
  }

  toggle(): void {
    this.darkMode.update((v) => !v);
  }

  /** Choix sauvegardé, sinon préférence système. */
  private resolveInitial(): boolean {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      return saved === 'dark';
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }
}
