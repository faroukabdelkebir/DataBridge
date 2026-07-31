/** Modèles de la page Analytics — miroir des DTOs de MonitoringMS. (v2) */

export interface AnalyticsSummary {
  totalClients: number;
  ageMoyen: number | null;
  nbGestionnaires: number;
  nbVirements: number;
  deltaVirementsPct: number | null;
  nbFactures: number;
  deltaFacturesPct: number | null;
  montantVirements: number | null;   // null tant que AMOUNT_DEBITED n'est pas extraite
  devisePrincipale: string | null;
}

export interface MonthlyFlow {
  mois: number;                      // 1..12
  nbOperations: number;
  nbOperationsN1: number | null;
  montantTotal: number | null;
  montantTotalN1: number | null;
}

export interface BreakdownSlice {
  label: string;
  value: number;
  pct: number;
}

export interface OfficerPerf {
  officer: string;
  nbClients: number;
  ageMoyen: number | null;
  partPct: number;
}

export interface Insight {
  type: 'LEADER' | 'CONCENTRATION' | 'PIC_ACTIVITE';
  titleKey: string;
  value: string;
  detail: string;                          // fallback brut (ex. code devise)
  detailKey?: string | null;               // clé i18n du complément (interpolable)
  params?: Record<string, unknown> | null; // paramètres d'interpolation
}

/* ── Page Flux ─────────────────────────────────────────────── */

export interface FlowsSummary {
  nbOperations: number;
  deltaOpsPct: number | null;
  montantTotal: number | null;       // null tant que AMOUNT_DEBITED absente
  deltaMontantPct: number | null;
  ticketMoyen: number | null;
  deviseDominante: string | null;
  nbDevises: number | null;
  nbTypes: number | null;
}

export interface FlowSlice {
  label: string;
  nbOperations: number;
  montantTotal: number | null;
  ticketMoyen: number | null;
  pct: number;                       // part du montant si dispo, sinon du volume
}

export interface HeatmapCell {
  mois: number;                      // 1..12
  jourSemaine: number;               // 0 = lundi … 6 = dimanche
  nb: number;
}

/* ── Page Clients ──────────────────────────────────────────── */

export interface CustomersSummary {
  totalClients: number;
  ageMoyen: number | null;
  nbGestionnaires: number;
  pctMigration: number | null;      // % de dossiers issus de la migration initiale
  nouveauxClients: number | null;   // null tant que DATE_AC_OPND absente
  deltaNouveauxPct: number | null;
  pctFemmes: number | null;         // null tant que GENDER absente
  nbNationalites: number | null;    // null tant que NATIONALITY absente
  nbSecteurs: number | null;        // null tant que SECTOR absente
}

/* ── Page Créances ─────────────────────────────────────────── */

export interface BillingSummary {
  nbFactures: number;
  deltaFacturesPct: number | null;
  montantFacture: number | null;       // null tant que OR_TOTAL_AMOUNT absente
  encoursTotal: number | null;         // null tant que OS_TOTAL_AMOUNT absente
  tauxRecouvrement: number | null;     // 100 × (1 − encours / facturé)
  pctPayees: number | null;            // null tant que SETTLE_STATUS absente
  delaiMoyenReglement: number | null;  // null tant que PAYMENT/ACTUAL_PAY_DATE absentes
}
