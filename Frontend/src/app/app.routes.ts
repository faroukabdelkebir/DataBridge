import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: '',
    loadComponent: () => import('./layout/main-layout/main-layout').then((m) => m.MainLayout),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard) },
      { path: 'analytics',
        loadComponent: () => import('./features/analytics/analytics').then((m) => m.Analytics) },
      { path: 'analytics/flows',
        loadComponent: () => import('./features/analytics/flows').then((m) => m.Flows) },
      { path: 'analytics/customers',
        loadComponent: () => import('./features/analytics/customers').then((m) => m.CustomersAnalytics) },
      { path: 'analytics/billing',
        loadComponent: () => import('./features/analytics/billing').then((m) => m.BillingAnalytics) },
      { path: 'profile',
        loadComponent: () => import('./features/profile/profile').then((m) => m.Profile) },
      { path: 'jobs',
        canActivate: [roleGuard('ADMIN', 'MONITOR')],
        loadComponent: () => import('./features/jobs/jobs').then((m) => m.Jobs) },
      { path: 'alerts',
        canActivate: [roleGuard('ADMIN', 'MONITOR')],
        loadComponent: () => import('./features/alerts/alerts').then((m) => m.Alerts) },
      { path: 'sla',
        canActivate: [roleGuard('ADMIN', 'MONITOR')],
        loadComponent: () => import('./features/sla/sla').then((m) => m.Sla) },
      { path: 'reconciliation',
        canActivate: [roleGuard('ADMIN', 'MONITOR')],
        loadComponent: () => import('./features/reconciliation/reconciliation').then((m) => m.Reconciliation) },
      { path: 'schema',
        canActivate: [roleGuard('ADMIN', 'MONITOR')],
        loadComponent: () => import('./features/schema/schema').then((m) => m.Schema) },
      { path: 'assistant',
        loadComponent: () => import('./features/assistant/assistant').then((m) => m.Assistant) },
      { path: 'batch',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/batch-control/batch-control').then((m) => m.BatchControl) },
      { path: 'admin/users',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/admin/users/users').then((m) => m.Users) },
    ],
  },
  { path: '**', redirectTo: '' },
];