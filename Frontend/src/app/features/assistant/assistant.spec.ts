import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { DomSanitizer } from '@angular/platform-browser';
import { MessageService } from 'primeng/api';

import { Assistant } from './assistant';
import { AiAgentsService } from '../../core/services/ai-agents.service';
import { MonitoringService } from '../../core/services/monitoring.service';
import { QualityPdfService } from '../../core/services/quality-pdf.service';
import { AuthService } from '../../core/services/auth.service';

describe('Assistant (composant)', () => {
  let ai: {
    getHealth: ReturnType<typeof vi.fn>;
    quickScore: ReturnType<typeof vi.fn>;
    audit: ReturnType<typeof vi.fn>;
    getRecommendation: ReturnType<typeof vi.fn>;
    launchRecommendation: ReturnType<typeof vi.fn>;
    listReports: ReturnType<typeof vi.fn>;
    getReport: ReturnType<typeof vi.fn>;
  };
  let monitoring: { getSchemaSummary: ReturnType<typeof vi.fn> };
  let toast: { add: ReturnType<typeof vi.fn> };
  let isVisitor: boolean;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Assistant,
        { provide: AiAgentsService, useValue: ai },
        { provide: MonitoringService, useValue: monitoring },
        { provide: QualityPdfService, useValue: { build: vi.fn(), fileName: vi.fn() } },
        { provide: MessageService, useValue: toast },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
        { provide: DomSanitizer, useValue: { bypassSecurityTrustResourceUrl: (u: string) => u } },
        { provide: AuthService, useValue: { hasRole: () => isVisitor } },
      ],
    });
    return TestBed.inject(Assistant);
  }

  beforeEach(() => {
    isVisitor = false;
    ai = {
      getHealth: vi.fn().mockReturnValue(of({ ollama: { available: true, active_model: 'llama3', models: ['llama3'] } })),
      quickScore: vi.fn().mockReturnValue(of({ global_score: 90, grade: 'A' })),
      audit: vi.fn().mockReturnValue(of({ report_id: 7, report: { global_score: 80 } })),
      getRecommendation: vi.fn().mockReturnValue(of({ status: 'idle' })),
      launchRecommendation: vi.fn().mockReturnValue(of({ status: 'idle' })),
      listReports: vi.fn().mockReturnValue(of({ results: [] })),
      getReport: vi.fn().mockReturnValue(of({ id: 1 })),
    };
    monitoring = { getSchemaSummary: vi.fn().mockReturnValue(of([{ tableName: 'T1' }, { tableName: 'T2' }])) };
    toast = { add: vi.fn() };
  });

  afterEach(() => vi.useRealTimers());

  describe('initialisation', () => {
    it('charge santé, tables et sélectionne le modèle actif', () => {
      const comp = create();
      expect(comp.health()?.ollama.available).toBe(true);
      expect(comp.tables()).toEqual(['T1', 'T2']);
      expect(comp.selectedTable).toBe('T1');
      expect(comp.selectedModel).toBe('llama3');
    });

    it('marque healthError si la santé échoue', () => {
      ai.getHealth.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      expect(comp.healthError()).toBe(true);
    });

    it('mode visiteur : ne charge que l’historique', () => {
      isVisitor = true;
      const comp = create();
      expect(comp.isVisitor()).toBe(true);
      expect(ai.getHealth).not.toHaveBeenCalled();
      expect(ai.listReports).toHaveBeenCalled();
    });
  });

  describe('quickScore', () => {
    it('ne fait rien sans table sélectionnée', () => {
      const comp = create();
      comp.selectedTable = '';
      comp.quickScore();
      expect(ai.quickScore).not.toHaveBeenCalled();
    });

    it('remplit le rapport sur succès', () => {
      const comp = create();
      comp.selectedTable = 'T1';
      comp.quickScore();
      expect(ai.quickScore).toHaveBeenCalledWith('T1', 'llama3');
      expect(comp.report()?.grade).toBe('A');
      expect(comp.scoring()).toBe(false);
    });
  });

  describe('fullAudit', () => {
    it('remplit le rapport, notifie et recharge l’historique', () => {
      const comp = create();
      comp.selectedTable = 'T1';
      comp.fullAudit();
      expect(comp.report()?.global_score).toBe(80);
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
      expect(ai.listReports).toHaveBeenCalled();
    });
  });

  describe('dimensionRows', () => {
    it('mappe les dimensions vers leurs libellés', () => {
      const comp = create();
      comp.report.set({ dimensions: { D1: { score: 90 }, D3: { score: 40 } } });
      const rows = comp.dimensionRows();
      expect(rows).toEqual([
        { key: 'D1', label: 'Complétude', score: 90 },
        { key: 'D3', label: 'Couverture schéma', score: 40 },
      ]);
    });
  });

  describe('helpers de score', () => {
    it('gradeSeverity mappe les grades', () => {
      const comp = create();
      expect(comp.gradeSeverity('A')).toBe('success');
      expect(comp.gradeSeverity('B')).toBe('info');
      expect(comp.gradeSeverity('C')).toBe('warn');
      expect(comp.gradeSeverity('F')).toBe('danger');
      expect(comp.gradeSeverity(undefined)).toBe('danger');
    });

    it('scoreColor par seuils', () => {
      const comp = create();
      expect(comp.scoreColor(85)).toBe('#22c55e');
      expect(comp.scoreColor(60)).toBe('#f59e0b');
      expect(comp.scoreColor(20)).toBe('#ef4444');
    });
  });

  describe('refreshReco (polling)', () => {
    it('démarre le polling quand le statut est "computing"', () => {
      vi.useFakeTimers();
      ai.getRecommendation.mockReturnValue(of({ status: 'computing' }));
      const comp = create();
      expect(comp.reco()?.status).toBe('computing');

      // getRecommendation appelé une fois à l'init ; le polling en ajoute d'autres
      const callsBefore = ai.getRecommendation.mock.calls.length;
      vi.advanceTimersByTime(5_000);
      expect(ai.getRecommendation.mock.calls.length).toBeGreaterThan(callsBefore);
    });
  });

  describe('openDetail', () => {
    it('charge le détail complet du rapport', () => {
      ai.getReport.mockReturnValue(of({ id: 42, agent: 'x' }));
      const comp = create();
      comp.openDetail({ id: 42 } as never);
      expect(comp.detailVisible()).toBe(true);
      expect(comp.detail()).toEqual({ id: 42, agent: 'x' });
    });

    it('retombe sur la ligne fournie si le détail échoue', () => {
      ai.getReport.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      const row = { id: 5 } as never;
      comp.openDetail(row);
      expect(comp.detail()).toBe(row);
    });
  });

  it('agentError affiche un toast seulement sur 503', () => {
    const comp = create();
    comp.launchReco();
    expect(toast.add).not.toHaveBeenCalled(); // succès idle, pas d'erreur

    ai.launchRecommendation.mockReturnValue(throwError(() => ({ status: 503, error: { error: 'ollama down' } })));
    comp.launchReco();
    expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'ollama down' }));
  });
});
