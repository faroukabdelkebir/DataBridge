import { Injectable, effect, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { PrimeNG } from 'primeng/config';

export type Lang = 'fr' | 'en' | 'ar';

interface LangOption {
  code: Lang;
  label: string;
}

const STORAGE_KEY = 'bi_lang';
const RTL_LANGS: Lang[] = ['ar'];
const SUPPORTED: Lang[] = ['fr', 'en', 'ar'];

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private translate = inject(TranslateService);
  private primeng = inject(PrimeNG);

  readonly available: LangOption[] = [
    { code: 'fr', label: 'Fran\u00e7ais' },
    { code: 'en', label: 'English' },
    { code: 'ar', label: '\u0627\u0644\u0639\u0631\u0628\u064a\u0629' },
  ];

  readonly current = signal<Lang>(this.resolveInitial());

  constructor() {
    this.translate.addLangs(SUPPORTED);

    effect(() => {
      const lang = this.current();

      // 1) Charge la langue dans ngx-translate
      this.translate.use(lang);

      // 2) Applique langue + direction sur <html>
      const dir = RTL_LANGS.includes(lang) ? 'rtl' : 'ltr';
      document.documentElement.setAttribute('lang', lang);
      document.documentElement.setAttribute('dir', dir);

      // 3) Persiste le choix
      localStorage.setItem(STORAGE_KEY, lang);

      // 4) Traduit aussi les libellés internes de PrimeNG
      this.translate.get('primeng').subscribe((res) => {
        if (res && typeof res === 'object') {
          this.primeng.setTranslation(res);
        }
      });
    });
  }

  setLang(lang: Lang): void {
    this.current.set(lang);
  }

  isRtl(): boolean {
    return RTL_LANGS.includes(this.current());
  }

  private resolveInitial(): Lang {
    const saved = localStorage.getItem(STORAGE_KEY) as Lang | null;
    if (saved && SUPPORTED.includes(saved)) {
      return saved;
    }
    const browser = this.translate.getBrowserLang() as Lang;
    return SUPPORTED.includes(browser) ? browser : 'fr';
  }
}
