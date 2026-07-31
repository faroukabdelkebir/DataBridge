import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { provideTranslateService } from '@ngx-translate/core';

import { SmartReport } from './smart-report';

/** Crée le composant et fixe l'input `value` sans rendre le template. */
function withValue(value: unknown): SmartReport {
  const fixture = TestBed.createComponent(SmartReport);
  (fixture.componentRef as ComponentRef<SmartReport>).setInput('value', value);
  return fixture.componentInstance;
}

describe('SmartReport (composant)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslateService()] });
  });

  it('déplie la section racine (pas de titre pour la racine)', () => {
    const blocks = withValue({ table: 'CUSTOMERS', score: 88 }).blocks();
    // La racine est une section dépliée → on obtient directement son contenu keyvalue.
    expect(blocks[0].kind).toBe('keyvalue');
  });

  it('formate les paires clé/valeur simples avec libellés humains', () => {
    const comp = withValue({ table_name: 'CUST', should_launch: true, score: 1234.5 });
    const kv = comp.blocks()[0];
    if (kv.kind !== 'keyvalue') throw new Error('attendu keyvalue');

    const byLabel = Object.fromEntries(kv.pairs.map((p) => [p.label, p]));
    expect(byLabel['Table'].value).toBe('CUST');
    expect(byLabel['Table'].kind).toBe('text');
    expect(byLabel['À lancer'].kind).toBe('bool');
    expect(byLabel['Score'].kind).toBe('number');
  });

  it('rend un tableau pour un tableau d’objets homogènes simples', () => {
    const comp = withValue({
      jobs: [
        { batch_id: 'A', priority: 1 },
        { batch_id: 'B', priority: 2 },
      ],
    });
    // section racine dépliée → première (et unique) sous-section "Traitements"
    const section = comp.blocks().find((b) => b.kind === 'section');
    if (!section || section.kind !== 'section') throw new Error('section attendue');
    const table = section.blocks[0];
    if (table.kind !== 'table') throw new Error('table attendue');

    expect(table.columns.map((c) => c.key)).toEqual(['batch_id', 'priority']);
    expect(table.rows).toHaveLength(2);
    expect(table.rows[0]['batch_id'].value).toBe('A');
  });

  it('rend une liste de tags pour un tableau de valeurs simples', () => {
    const comp = withValue({ tables: ['T1', 'T2', 'T3'] });
    const section = comp.blocks().find((b) => b.kind === 'section');
    if (!section || section.kind !== 'section') throw new Error('section attendue');
    const list = section.blocks[0];
    expect(list.kind).toBe('list');
    if (list.kind === 'list') expect(list.items).toHaveLength(3);
  });

  it('affiche un tiret pour un tableau vide', () => {
    const comp = withValue({ tables: [] });
    const section = comp.blocks().find((b) => b.kind === 'section');
    if (!section || section.kind !== 'section') throw new Error('section attendue');
    const block = section.blocks[0];
    if (block.kind !== 'text') throw new Error('texte attendu');
    expect(block.kv.value).toBe('—');
    expect(block.kv.kind).toBe('empty');
  });

  describe('boolSeverity', () => {
    it('success pour les valeurs affirmatives, secondary sinon', () => {
      const comp = withValue({});
      expect(comp.boolSeverity({ label: 'x', value: 'Oui', kind: 'bool' })).toBe('success');
      expect(comp.boolSeverity({ label: 'x', value: 'true', kind: 'bool' })).toBe('success');
      expect(comp.boolSeverity({ label: 'x', value: 'Non', kind: 'bool' })).toBe('secondary');
    });
  });
});
