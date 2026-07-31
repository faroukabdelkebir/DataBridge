import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';

/**
 * Onglets de navigation entre les pages analytiques.
 * À placer sous l'en-tête de chaque page : <app-analytics-tabs />
 */
@Component({
  selector: 'app-analytics-tabs',
  imports: [RouterLink, RouterLinkActive, TranslatePipe],
  template: `
    <nav class="ana-tabs">
      <a routerLink="/analytics" routerLinkActive="active"
         [routerLinkActiveOptions]="{ exact: true }">
        <i class="pi pi-chart-pie"></i>
        {{ 'analytics.tabs.overview' | translate }}
      </a>
      <a routerLink="/analytics/flows" routerLinkActive="active">
        <i class="pi pi-arrows-h"></i>
        {{ 'analytics.tabs.flows' | translate }}
      </a>
      <a routerLink="/analytics/customers" routerLinkActive="active">
        <i class="pi pi-users"></i>
        {{ 'analytics.tabs.customers' | translate }}
      </a>
      <a routerLink="/analytics/billing" routerLinkActive="active">
        <i class="pi pi-receipt"></i>
        {{ 'analytics.tabs.billing' | translate }}
      </a>
    </nav>
  `,
  styles: `
    .ana-tabs {
      display: flex;
      gap: .25rem;
      margin-bottom: 1.25rem;
      border-bottom: 1px solid var(--app-border);

      a {
        display: flex;
        align-items: center;
        gap: .45rem;
        padding: .55rem .9rem;
        font-size: .88rem;
        font-weight: 500;
        opacity: .65;
        border-bottom: 2px solid transparent;
        margin-bottom: -1px;
        transition: opacity .15s ease;

        i { font-size: .85rem; }

        &:hover { opacity: 1; }

        &.active {
          opacity: 1;
          color: var(--brand-accent, #6366f1);
          border-bottom-color: var(--brand-accent, #6366f1);
        }
      }
    }
  `,
})
export class AnalyticsTabs {}
