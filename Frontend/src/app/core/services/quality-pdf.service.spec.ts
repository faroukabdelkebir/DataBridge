import { describe, it, expect, beforeEach } from 'vitest';

import { QualityPdfService } from './quality-pdf.service';
import { QualityReport } from '../models/ai-agents.model';

describe('QualityPdfService', () => {
  let service: QualityPdfService;

  beforeEach(() => {
    service = new QualityPdfService();
  });

  describe('fileName', () => {
    it('assainit le nom de table et ajoute la date du jour', () => {
      const name = service.fileName({ table: 'DB.CUST OMERS/2024' });
      expect(name).toMatch(/^qualite_DB\.CUST_OMERS_2024_\d{4}-\d{2}-\d{2}\.pdf$/);
    });

    it('utilise "rapport" quand la table est absente', () => {
      expect(service.fileName({})).toMatch(/^qualite_rapport_\d{4}-\d{2}-\d{2}\.pdf$/);
    });
  });

  describe('build', () => {
    it('produit un Blob PDF non vide', () => {
      const report: QualityReport = {
        table: 'CUSTOMERS',
        global_score: 87.5,
        grade: 'A',
        dimensions: {
          D1: { score: 90 },
          D2: { score: 60 },
          D3: { score: 30 },
        },
        interpretation: 'La qualité globale est bonne malgré une couverture schéma faible.',
        improvements_by_impact: [
          { dimension: 'D3', current: 30, max_gain: 20, action: 'Compléter les colonnes manquantes.' },
        ],
      };

      const blob = service.build(report);
      expect(blob).toBeInstanceOf(Blob);
      expect(blob.type).toBe('application/pdf');
      expect(blob.size).toBeGreaterThan(0);
    });

    it('gère un rapport minimal sans dimensions ni actions', () => {
      const blob = service.build({});
      expect(blob).toBeInstanceOf(Blob);
      expect(blob.size).toBeGreaterThan(0);
    });
  });
});
