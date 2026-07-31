import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { provideTranslateService } from '@ngx-translate/core';

import { KpiCard } from './kpi-card';
import { Kpi } from '../../../core/models/kpi.model';

// Euro (U+20AC) + espace insecable (U+00A0), tels que produits par le composant.
const EURO_NBSP = String.fromCharCode(0x20ac, 0x00a0);

beforeEach(() => {
  // Le template importe TranslatePipe → TranslateService doit être fourni,
  // même si on ne rend jamais le template.
  TestBed.configureTestingModule({ providers: [provideTranslateService()] });
});

function baseKpi(overrides: Partial<Kpi> = {}): Kpi {
  return {
    key: 'revenue', label: 'CA', value: 1000, format: 'number',
    delta: 5, icon: 'pi pi-euro', color: '#333',
    ...overrides,
  };
}

/**
 * On cree le composant sans declencher la detection de changements :
 * le template n'est jamais rendu, on n'evalue que les signaux computed.
 */
function withInput(kpi: Kpi): KpiCard {
  const fixture = TestBed.createComponent(KpiCard);
  const ref = fixture.componentRef as ComponentRef<KpiCard>;
  ref.setInput('kpi', kpi);
  return fixture.componentInstance;
}

describe('KpiCard (composant)', () => {
  it('hasDelta est faux quand delta est null', () => {
    expect(withInput(baseKpi({ delta: null })).hasDelta()).toBe(false);
    expect(withInput(baseKpi({ delta: 0 })).hasDelta()).toBe(true);
  });

  it('isPositive suit le signe du delta', () => {
    expect(withInput(baseKpi({ delta: 3 })).isPositive()).toBe(true);
    expect(withInput(baseKpi({ delta: 0 })).isPositive()).toBe(true);
    expect(withInput(baseKpi({ delta: -2 })).isPositive()).toBe(false);
  });

  it('suffix ajoute le pourcentage pour le format percent', () => {
    expect(withInput(baseKpi({ format: 'percent' })).suffix()).toBe('%');
    expect(withInput(baseKpi({ format: 'number' })).suffix()).toBe('');
  });

  it('prefix ajoute le symbole euro + espace insecable pour le format currency', () => {
    expect(withInput(baseKpi({ format: 'currency' })).prefix()).toBe(EURO_NBSP);
    expect(withInput(baseKpi({ format: 'number' })).prefix()).toBe('');
  });
});
