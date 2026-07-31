/**
 * Fichier d'initialisation global des tests (Vitest + jsdom).
 * Le builder @angular/build:unit-test initialise automatiquement
 * l'environnement de test Angular (TestBed) ; on ne configure ici
 * que les compléments transverses aux specs.
 */
import { afterEach, vi } from 'vitest';

// jsdom ne fournit pas createObjectURL / revokeObjectURL (utilisés par l'export CSV).
if (!('createObjectURL' in URL)) {
  (URL as unknown as { createObjectURL: () => string }).createObjectURL = () => 'blob:mock';
}
if (!('revokeObjectURL' in URL)) {
  (URL as unknown as { revokeObjectURL: () => void }).revokeObjectURL = () => undefined;
}

afterEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});
