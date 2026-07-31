# BI Dashboard — Template Angular 21

Template de **dashboard Business Intelligence** prêt à l'emploi, basé sur les
patterns modernes d'Angular 21 : **composants standalone**, **signals**,
**change detection zoneless**, nouvelle syntaxe de control flow (`@if` / `@for`),
lazy loading des routes, **PrimeNG** pour l'UI et **Apache ECharts** pour les graphiques.

## Ce qui est inclus

- **Authentification** : pages Login et Register (Reactive Forms + validation), `AuthService` à base de signals, garde de route fonctionnelle (`authGuard`) et intercepteur HTTP qui injecte le token Bearer.
- **Layout** : sidebar repliable + topbar avec menu profil.
- **Dashboard** : 4 cartes **KPI** réutilisables + graphiques **ECharts** (courbe revenus, barres commandes, donut par catégorie).
- **Tables** : `p-table` PrimeNG avec tri, recherche globale, pagination, tags de statut et export CSV.
- **Données** : couche `mock` en mémoire **et** service déjà câblé pour une **API REST** réelle — on bascule via un simple flag.
- **Boutons, inputs, password, tags, avatars, menus, toasts** : composants PrimeNG.

## Prérequis

- Node.js 20.19+ (ou 22+)
- npm 10+

## Installation

```bash
# 1. Installer les dépendances
npm install

# 2. Lancer le serveur de dev
npm start
# -> http://localhost:4200
```

> Aucune installation backend n'est requise : le template démarre en **mode mock**.
> Connecte-toi avec n'importe quel email/mot de passe valides (pré-remplis sur la page de login).

## Basculer mock <-> API réelle

Tout se règle dans `src/environments/` :

```ts
export const environment = {
  useMock: true,                 // false => appels HTTP réels
  apiUrl: 'http://localhost:3000/api',
};
```

- `useMock: true`  → les services renvoient les données de `src/app/core/mocks/`.
- `useMock: false` → les services appellent `apiUrl` via `HttpClient`.

Les endpoints attendus côté backend :

| Méthode | URL                         | Réponse              |
|---------|-----------------------------|----------------------|
| POST    | `/auth/login`               | `{ token, user }`    |
| POST    | `/auth/register`            | `{ token, user }`    |
| GET     | `/dashboard/kpis`           | `Kpi[]`              |
| GET     | `/dashboard/revenue`        | `TimeSeriesPoint[]`  |
| GET     | `/dashboard/orders`         | `TimeSeriesPoint[]`  |
| GET     | `/dashboard/categories`     | `CategorySlice[]`    |
| GET     | `/dashboard/transactions`   | `Transaction[]`      |

## Structure du projet

```
src/app/
├── app.config.ts          # providers (zoneless, router, http, PrimeNG, ECharts)
├── app.routes.ts          # routes lazy + garde d'auth
├── app.ts                 # composant racine (router-outlet + toast)
├── core/
│   ├── models/            # interfaces TypeScript
│   ├── services/          # AuthService, DashboardService (mock + API)
│   ├── guards/            # authGuard (CanActivateFn)
│   ├── interceptors/      # authInterceptor (HttpInterceptorFn)
│   └── mocks/             # données de démonstration
├── layout/
│   └── main-layout/       # sidebar + topbar
├── shared/
│   └── components/kpi-card # carte KPI réutilisable (signal input)
└── features/
    ├── auth/login         # page de connexion
    ├── auth/register      # page d'inscription
    ├── dashboard          # KPIs + graphiques ECharts
    └── tables             # table PrimeNG des transactions
```

## Conventions Angular 21 utilisées

- **Standalone par défaut** : plus de `NgModule`, on importe directement dans `imports: []`.
- **Nommage moderne** : les composants n'ont pas le suffixe `.component` (classe `Dashboard` dans `dashboard.ts`). Les services gardent `.service` pour la lisibilité.
- **Signals** : `signal()`, `computed()`, `input.required()` pour l'état et les inputs.
- **Zoneless** : `provideZonelessChangeDetection()` — pas de `zone.js`.
- **Control flow** : `@if`, `@for`, `@empty` directement dans les templates.
- **DI fonctionnelle** : `inject()`, gardes et intercepteurs sous forme de fonctions.

> Astuce : Angular 21 introduit les **Signal Forms** (expérimentales). Ce template
> utilise les Reactive Forms (stables) pour les formulaires d'auth ; tu peux migrer
> vers `@angular/forms/signals` quand l'API se stabilisera.

## Build de production

```bash
npm run build      # sortie dans dist/bi-dashboard
```

## Personnalisation du thème PrimeNG

Le thème **Aura** est configuré dans `app.config.ts`. Pour passer en mode sombre,
ajoute la classe `app-dark` sur `<html>` (le sélecteur est déjà déclaré). Pour
changer de preset (Lara, Nora…), remplace simplement l'import `@primeng/themes/aura`.
