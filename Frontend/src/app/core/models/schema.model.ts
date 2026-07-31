// ─────────────────────────────────────────────────────────────
// MonitoringMS /schema — structure des tables sources T24
// ─────────────────────────────────────────────────────────────

/** GET /schema/summary — résumé par table. */
export interface TableSchemaSummary {
  tableName: string;
  totalColumns: number;
  enabledColumns: number;
  indexedColumns: number;
  mvColumns: number;           // colonnes multi-valeurs
}

/** GET /schema/columns — définition d'une colonne source. */
export interface SchemaSourceRecord {
  id: string;
  tableName: string;
  columnName: string;
  dataType: string;
  enabled: boolean;
  indexed: boolean;
  ssTag: string | null;
  singleMult: 'S' | 'M' | string;  // 'S' = mono-valeur, 'M' = multi-valeur
  localRef: boolean;
  insertDate: number | null;      // epoch millis
}

/** GET /schema/audit — historique des modifications de schéma. */
export interface SchemaAuditRecord {
  id: number;
  sourceId: string;
  sourceTableName: string;
  sourceColumnName: string;
  sourceDataType: string;
  newSourceEnabled: number;    // 0 | 1
  oldSourceEnabled: number;
  newSourceIndexed: number;
  oldSourceIndexed: number;
  updatedBy: string | null;
  updatedOn: number | null;    // epoch millis
}