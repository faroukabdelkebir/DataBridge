// ─────────────────────────────────────────────────────────────
// Service AI Agents (Django) — contrats relevés dans les views
// ─────────────────────────────────────────────────────────────

export interface AiHealth {
  service: string;
  status: string;
  ollama: {
    available: boolean;
    models: string[];
    active_model: string;
  };
  agents: Record<string, string>;
}

// ── M1 : Qualité / Anomalies ─────────────────────────────────

export interface QualityImprovement {
  dimension: string;       // 'D1'..'D6'
  current: number;
  max_gain: number;
  action: string;
}

export interface QualityReport {
  table?: string;
  global_score?: number;
  grade?: string;          // 'A'..'F'
  dimensions?: Record<string, { score: number; [k: string]: unknown }>;
  improvements_by_impact?: QualityImprovement[];
  interpretation?: string; // synthèse rédigée par le SLM
  [k: string]: unknown;
}

export interface AuditResponse {
  status: string;
  report_id: number;
  report: QualityReport;
}

export interface AuditAllReport {
  reports: Record<string, QualityReport>;
  ranking: { table: string; global_score: number; grade: string }[];
  worst_table: string;
  total_iterations?: number;
  [k: string]: unknown;
}

// ── M3 : Ordonnancement (asynchrone, à poller) ───────────────

export type RecoStatus = 'idle' | 'computing' | 'completed' | 'error';

export interface SchedulerReco {
  status: RecoStatus;
  started_at: string | null;
  computed_at: string | null;
  duration_sec: number | null;
  error: string | null;
  report: unknown;         // plan généré par le SLM — forme libre
}

// ── Historique des rapports ──────────────────────────────────

export interface AgentReportRow {
  id: number;
  agent: string;           // 'anomaly_quality' | 'scheduler'
  report_type: string;     // 'audit_table' | 'audit_all' | 'quick_score' | ...
  target: string | null;
  model: string | null;
  status: string;
  report?: unknown;        // présent sur le détail
  error: string | null;
  duration_sec: number | null;
  created_at: string;
}

export interface ReportList {
  count: number;
  results: AgentReportRow[];
}