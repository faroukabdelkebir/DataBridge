# Tests unitaires & de régression

Stack : **Vitest 4** via le builder officiel Angular 21 `@angular/build:unit-test` (jsdom, projet zoneless).

## Lancer les tests

```bash
npm test          # exécution unique (CI)
npm run test:watch # mode watch
```

Configuration : cible `test` dans [`angular.json`](../../angular.json), [`tsconfig.spec.json`](../../tsconfig.spec.json),
initialisation globale dans [`src/test-setup.ts`](../test-setup.ts) (polyfills jsdom, purge du `localStorage`).

## Conventions

- Un fichier `*.spec.ts` à côté de chaque unité testée.
- Import explicite des globals depuis `vitest` (`describe`, `it`, `expect`, `vi`, …) —
  robuste vis-à-vis du serveur TypeScript de l'IDE.
- **Services / interceptors** : `TestBed` + `provideHttpClient()` / `provideHttpClientTesting()`,
  assertions sur URL, verbe HTTP et query params (tests de **régression** du contrat HTTP).
- **Composants** : instanciés via `TestBed.inject(Composant)` avec services mockés — on teste la
  logique métier (signals, computed, méthodes) **sans rendre le template PrimeNG**. Pour les
  composants à `input.required` (kpi-card, smart-report), `TestBed.createComponent` + `setInput`
  sans détection de changements.
- **Effets/signals** : `TestBed.tick()` pour exécuter les `effect()`.
- **SSE / EventSource** : `EventSource` remplacé par un double de test (voir `job-stream.service.spec.ts`).

## Périmètre couvert

Services (auth, monitoring, user, analytics, ai-agents, extractor, theme, language, job-stream,
quality-pdf), interceptors (auth, error), guards (auth, role), pipe (duration), et la logique des
composants (auth, jobs, alerts, sla, reconciliation, schema, profile, batch-control, admin/users,
dashboard, analytics/flows/billing/customers, assistant, main-layout, kpi-card, smart-report, app).

Non couverts (volontairement) : templates HTML/rendu ECharts (options de graphes), et le code
template mort à imports cassés (`dashboard.service.ts`, `features/tables`, `auth/register` —
hors routes).
