import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { Kpi } from '../../../core/models/kpi.model';

@Component({
  selector: 'app-kpi-card',
  imports: [DecimalPipe, TranslatePipe],
  templateUrl: './kpi-card.html',
  styleUrl: './kpi-card.scss',
})
export class KpiCard {
  readonly kpi = input.required<Kpi>();

  readonly hasDelta = computed(() => this.kpi().delta !== null);
  readonly isPositive = computed(() => (this.kpi().delta ?? 0) >= 0);
  readonly suffix = computed(() => (this.kpi().format === 'percent' ? '%' : ''));
  readonly prefix = computed(() => (this.kpi().format === 'currency' ? '\u20ac\u00a0' : ''));
}