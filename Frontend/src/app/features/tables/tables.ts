import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';

import { DashboardService } from '../../core/services/dashboard.service';
import { Transaction, TransactionStatus } from '../../core/models/transaction.model';

type Severity = 'success' | 'warn' | 'danger' | 'info';

@Component({
  selector: 'app-tables',
  imports: [
    FormsModule, CurrencyPipe, DatePipe, TranslatePipe,
    TableModule, TagModule, ButtonModule,
    InputTextModule, IconFieldModule, InputIconModule,
  ],
  templateUrl: './tables.html',
  styleUrl: './tables.scss',
})
export class Tables {
  private service = inject(DashboardService);

  readonly transactions = signal<Transaction[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.service.getTransactions().subscribe((data) => {
      this.transactions.set(data);
      this.loading.set(false);
    });
  }

  statusSeverity(status: TransactionStatus): Severity {
    switch (status) {
      case 'completed': return 'success';
      case 'pending': return 'warn';
      case 'failed': return 'danger';
    }
  }
}
