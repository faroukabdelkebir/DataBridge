import { Component, computed, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

/** Bloc d'affichage produit par le transformateur JSON → vue lisible. */
type ValueKind = 'text' | 'number' | 'bool' | 'date' | 'empty';

interface KeyValue { label: string; value: string; kind: ValueKind; }

type ReportBlock =
  | { kind: 'section'; title: string; blocks: ReportBlock[] }
  | { kind: 'table'; columns: { key: string; label: string }[]; rows: Record<string, KeyValue>[] }
  | { kind: 'keyvalue'; pairs: KeyValue[] }
  | { kind: 'list'; items: KeyValue[] }
  | { kind: 'text'; kv: KeyValue };

/** Libellés « humains » pour les clés techniques les plus courantes du plan. */
const LABELS: Record<string, string> = {
  batch_id: 'Traitement',
  batch: 'Traitement',
  table: 'Table',
  table_name: 'Table',
  priority: 'Priorité',
  score: 'Score',
  reason: 'Justification',
  recommendation: 'Recommandation',
  recommended: 'Recommandé',
  should_launch: 'À lancer',
  launch: 'Lancement',
  next_run: 'Prochaine exécution',
  scheduled_at: 'Planifié pour',
  recommended_time: 'Horaire conseillé',
  window: 'Fenêtre',
  frequency: 'Fréquence',
  status: 'Statut',
  duration_sec: 'Durée (s)',
  confidence: 'Confiance',
  order: 'Ordre',
  rank: 'Rang',
  plan: 'Plan',
  summary: 'Synthèse',
  actions: 'Actions',
  jobs: 'Traitements',
  tables: 'Tables',
  notes: 'Remarques',
};

/**
 * Rend n'importe quelle structure JSON (« forme libre ») sous une forme
 * lisible par un utilisateur non technique : sections, tableaux, listes,
 * paires clé/valeur, avec dates et booléens formatés.
 */
@Component({
  selector: 'app-smart-report',
  imports: [CommonModule, TableModule, TagModule],
  templateUrl: './smart-report.html',
  styleUrl: './smart-report.scss',
})
export class SmartReport {
  private translate = inject(TranslateService);

  readonly value = input.required<unknown>();

  readonly blocks = computed<ReportBlock[]>(() => {
    const v = this.value();
    const root = this.toBlock(v, '');
    // Une section racine : on n'affiche pas son titre, seulement son contenu.
    return root.kind === 'section' ? root.blocks : [root];
  });

  // ── Transformation récursive ─────────────────────────────────
  private toBlock(value: unknown, key: string): ReportBlock {
    if (Array.isArray(value)) return this.arrayToBlock(value, key);
    if (this.isPlainObject(value)) return this.objectToBlock(value, key);
    return { kind: 'text', kv: this.kv(key, value) };
  }

  private objectToBlock(obj: Record<string, unknown>, key: string): ReportBlock {
    const entries = Object.entries(obj);
    const simple: KeyValue[] = [];
    const complex: ReportBlock[] = [];

    for (const [k, val] of entries) {
      if (this.isComplex(val)) {
        const child = this.toBlock(val, k);
        // On force un titre de section pour les sous-structures.
        complex.push(child.kind === 'section' ? child
          : { kind: 'section', title: this.humanize(k), blocks: [child] });
      } else {
        simple.push(this.kv(k, val));
      }
    }

    const blocks: ReportBlock[] = [];
    if (simple.length) blocks.push({ kind: 'keyvalue', pairs: simple });
    blocks.push(...complex);

    return { kind: 'section', title: this.humanize(key), blocks };
  }

  private arrayToBlock(arr: unknown[], key: string): ReportBlock {
    if (arr.length === 0) {
      return { kind: 'text', kv: { label: this.humanize(key), value: '—', kind: 'empty' } };
    }

    // Tableau d'objets homogènes → table.
    if (arr.every((it) => this.isPlainObject(it))) {
      const objs = arr as Record<string, unknown>[];
      const keys: string[] = [];
      for (const o of objs) for (const k of Object.keys(o)) if (!keys.includes(k)) keys.push(k);
      // Uniquement si les valeurs sont majoritairement simples (sinon liste de sections).
      const allSimple = objs.every((o) => keys.every((k) => !this.isComplex(o[k])));
      if (allSimple) {
        const columns = keys.map((k) => ({ key: k, label: this.humanize(k) }));
        const rows = objs.map((o) => {
          const row: Record<string, KeyValue> = {};
          for (const k of keys) row[k] = this.kv(k, o[k] ?? null);
          return row;
        });
        return { kind: 'section', title: this.humanize(key), blocks: [{ kind: 'table', columns, rows }] };
      }
      // Objets imbriqués → une sous-section par élément.
      const blocks = objs.map((o, i) => {
        const sub = this.objectToBlock(o, `${key} ${i + 1}`);
        return sub;
      });
      return { kind: 'section', title: this.humanize(key), blocks };
    }

    // Tableau de valeurs simples → liste de tags.
    const items = arr.map((it) => this.kv('', it));
    return { kind: 'section', title: this.humanize(key), blocks: [{ kind: 'list', items }] };
  }

  // ── Helpers ──────────────────────────────────────────────────
  private isPlainObject(v: unknown): v is Record<string, unknown> {
    return v !== null && typeof v === 'object' && !Array.isArray(v);
  }

  private isComplex(v: unknown): boolean {
    return Array.isArray(v) || this.isPlainObject(v);
  }

  private kv(key: string, value: unknown): KeyValue {
    return { label: this.humanize(key), ...this.formatValue(value) };
  }

  private formatValue(value: unknown): { value: string; kind: ValueKind } {
    if (value === null || value === undefined || value === '') {
      return { value: '—', kind: 'empty' };
    }
    if (typeof value === 'boolean') {
      return { value: this.t(value ? 'common.yes' : 'common.no', value ? 'Oui' : 'Non'), kind: 'bool' };
    }
    if (typeof value === 'number') {
      return { value: this.formatNumber(value), kind: 'number' };
    }
    const s = String(value);
    if (this.isIsoDate(s)) {
      const d = new Date(s);
      if (!isNaN(d.getTime())) {
        return { value: d.toLocaleString(this.locale()), kind: 'date' };
      }
    }
    return { value: s, kind: 'text' };
  }

  private formatNumber(n: number): string {
    if (Number.isInteger(n)) return n.toLocaleString(this.locale());
    return n.toLocaleString(this.locale(), { maximumFractionDigits: 2 });
  }

  private locale(): string {
    const lang = this.translate.currentLang;
    return (typeof lang === 'function' ? lang() : lang) || 'fr';
  }

  private isIsoDate(s: string): boolean {
    return /^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2})?/.test(s) && s.length >= 8 && s.length <= 32;
  }

  private humanize(key: string): string {
    if (!key) return '';
    const known = LABELS[key.toLowerCase()];
    if (known) return known;
    const spaced = key
      .replace(/[_\-]+/g, ' ')
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .trim();
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  private t(key: string, fallback: string): string {
    const val = this.translate.instant(key);
    return val === key ? fallback : val;
  }

  /** Sévérité PrimeNG pour un tag booléen. */
  boolSeverity(kv: KeyValue): 'success' | 'secondary' {
    return /oui|yes|true|نعم/i.test(kv.value) ? 'success' : 'secondary';
  }
}
