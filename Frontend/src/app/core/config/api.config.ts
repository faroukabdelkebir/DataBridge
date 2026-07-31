import { environment } from '../../../environments/environment';

const GW = environment.gatewayUrl;

/**
 * Routes Kong (kong.yml) :
 *   /authms     -> AuthMS       :8090
 *   /userms     -> UserMS       :8085
 *   /monitoring -> MonitoringMS :65010
 *   /extractor  -> ExtractorMS  :65005
 *   /aiagents   -> AI Agents    :65015
 */
export const API = {
  auth: {
    login:          `${GW}/authms/api/v1/auth/login`,
    logout:         `${GW}/authms/api/v1/auth/logout`,
    logoutAll:      `${GW}/authms/api/v1/auth/logout-all`,
    refresh:        `${GW}/authms/api/v1/auth/refresh`,
    forgotPassword: `${GW}/authms/api/v1/auth/forgot-password`,
    resetPassword:  `${GW}/authms/api/v1/auth/reset-password`,
    changePassword: `${GW}/authms/api/v1/auth/change-password`,
    me:             `${GW}/authms/api/v1/auth/me`,
    audit:          `${GW}/authms/api/v1/auth/audit`,
  },

  users: {
    base:       `${GW}/authms/bff/users`,
    byId:       (id: string) => `${GW}/authms/bff/users/${id}`,
    activate:   (id: string) => `${GW}/authms/bff/users/${id}/activate`,
    deactivate: (id: string) => `${GW}/authms/bff/users/${id}/deactivate`,
  },

  monitoring: {
    dashboardSummary: `${GW}/monitoring/api/v1/dashboard/summary`,
    liveJobs:         `${GW}/monitoring/api/v1/dashboard/jobs/live`,

    jobs:        `${GW}/monitoring/api/v1/jobs`,
    jobsHistory: `${GW}/monitoring/api/v1/jobs/history`,
    jobById:     (id: number) => `${GW}/monitoring/api/v1/jobs/${id}`,
    jobSteps:    (id: number) => `${GW}/monitoring/api/v1/jobs/${id}/steps`,
    jobsByName:  (name: string) => `${GW}/monitoring/api/v1/jobs/by-name/${name}`,

    kpis:                  `${GW}/monitoring/api/v1/kpis`,
    reconciliation:        `${GW}/monitoring/api/v1/kpis/reconciliation`,
    reconciliationByTable: (table: string) => `${GW}/monitoring/api/v1/kpis/reconciliation/${table}`,
    sla:                   `${GW}/monitoring/api/v1/kpis/sla`,
    slaByJob:              (jobName: string) => `${GW}/monitoring/api/v1/kpis/sla/${jobName}`,

    alerts:       `${GW}/monitoring/api/v1/alerts`,
    resolveAlert: (key: string) => `${GW}/monitoring/api/v1/alerts/${encodeURIComponent(key)}`,

    schemaSummary:        `${GW}/monitoring/api/v1/schema/summary`,
    schemaColumns:        `${GW}/monitoring/api/v1/schema/columns`,
    schemaColumnsByTable: (table: string) => `${GW}/monitoring/api/v1/schema/columns/${table}`,
    schemaAudit:          `${GW}/monitoring/api/v1/schema/audit`,
    schemaAuditByTable:   (table: string) => `${GW}/monitoring/api/v1/schema/audit/${table}`,

    reportDaily:  `${GW}/monitoring/api/v1/reports/daily`,
    reportWeekly: `${GW}/monitoring/api/v1/reports/weekly`,

    configBatch:     `${GW}/monitoring/api/v1/config/batch`,
    configBatchById: (id: string) => `${GW}/monitoring/api/v1/config/batch/${id}`,

    // SSE — flux temps réel des jobs (text/event-stream)
    streamJobs: `${GW}/monitoring/api/v1/stream/jobs`,
    analytics: {
      summary:            `${GW}/monitoring/api/v1/analytics/summary`,
      transfersMonthly:   `${GW}/monitoring/api/v1/analytics/transfers/monthly`,
      agePyramid:         `${GW}/monitoring/api/v1/analytics/customers/age-pyramid`,
      byOfficer:          `${GW}/monitoring/api/v1/analytics/customers/by-officer`,
      billingAging:       `${GW}/monitoring/api/v1/analytics/billing/aging`,
      years:              `${GW}/monitoring/api/v1/analytics/years`,
      insights:           `${GW}/monitoring/api/v1/analytics/insights`,
      flowsSummary:       `${GW}/monitoring/api/v1/analytics/flows/summary`,
      flowsByCurrency:    `${GW}/monitoring/api/v1/analytics/flows/by-currency`,
      flowsByType:        `${GW}/monitoring/api/v1/analytics/flows/by-type`,
      flowsHeatmap:       `${GW}/monitoring/api/v1/analytics/flows/heatmap`,
      flowsColumnsStatus: `${GW}/monitoring/api/v1/analytics/flows/columns-status`,
      flowsInsights:      `${GW}/monitoring/api/v1/analytics/flows/insights`,
      customersSummary:          `${GW}/monitoring/api/v1/analytics/customers/summary`,
      customersAgeHistogram:     `${GW}/monitoring/api/v1/analytics/customers/age-histogram`,
      customersGenerations:      `${GW}/monitoring/api/v1/analytics/customers/generations`,
      customersNewMonthly:       `${GW}/monitoring/api/v1/analytics/customers/new-monthly`,
      customersBreakdown:        `${GW}/monitoring/api/v1/analytics/customers/breakdown`,
      customersDimensions:       `${GW}/monitoring/api/v1/analytics/customers/dimensions`,
      customersColumnsStatus:    `${GW}/monitoring/api/v1/analytics/customers/columns-status`,
      customersAcquisitionYears: `${GW}/monitoring/api/v1/analytics/customers/acquisition-years`,
      customersInsights:         `${GW}/monitoring/api/v1/analytics/customers/insights`,
      billingSummary:       `${GW}/monitoring/api/v1/analytics/billing/summary`,
      billingMonthly:       `${GW}/monitoring/api/v1/analytics/billing/monthly`,
      billingBreakdown:     `${GW}/monitoring/api/v1/analytics/billing/breakdown`,
      billingDimensions:    `${GW}/monitoring/api/v1/analytics/billing/dimensions`,
      billingColumnsStatus: `${GW}/monitoring/api/v1/analytics/billing/columns-status`,
      billingYears:         `${GW}/monitoring/api/v1/analytics/billing/years`,
      billingInsights:      `${GW}/monitoring/api/v1/analytics/billing/insights`,
    },
  },

  extractor: {
    startMain:     `${GW}/authms/bff/batch/start`,
    startCustomer: `${GW}/authms/bff/batch/customer/start`,
    startFtHis:    `${GW}/authms/bff/batch/ft-his/start`,
    startBills:    `${GW}/authms/bff/batch/billdetails/start`,
    stopMain:      `${GW}/authms/bff/batch/stop`,
    schemaColumns: (table: string) => `${GW}/authms/bff/batch/schema-source/${encodeURIComponent(table)}`,
    toggleColumn:  `${GW}/authms/bff/batch/schema-source/column/enabled`,
    schedulerConfigs: `${GW}/authms/bff/batch/scheduler/config`,
    schedulerUpdate:  (id: string) => `${GW}/authms/bff/batch/scheduler/config/${encodeURIComponent(id)}`,
    schedulerToggle:  (id: string) => `${GW}/authms/bff/batch/scheduler/config/${encodeURIComponent(id)}/enabled`,
  },

  aiAgents: {
    health:             `${GW}/aiagents/`,
    quickScore:         `${GW}/authms/bff/ai/api/m1/quick-score/`,
    audit:              `${GW}/authms/bff/ai/api/m1/audit/`,
    auditAll:           `${GW}/authms/bff/ai/api/m1/audit-all/`,
    schedulerRecommend: `${GW}/authms/bff/ai/api/m3/scheduler/recommend/`,
    shouldLaunch:       `${GW}/authms/bff/ai/api/m3/scheduler/should-launch/`,
    schedulerConfig:    `${GW}/authms/bff/ai/api/m3/scheduler/config/`,
    reports:            `${GW}/authms/bff/ai/api/reports/`,
    reportById: (id: number) => `${GW}/authms/bff/ai/api/reports/${id}/`,
  },
} as const;