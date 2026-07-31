/** GET /config/batch — configuration des traitements d'extraction. */
export interface BatchConfigRecord {
  id: string;
  tableName: string;
  t24SelectionName: string | null;
  colonneRef: string | null;
  chargement: string | null;
  lastDate: number | null;     // epoch millis
  minDate: number | null;
  enabled: boolean;
  scheduler: boolean;
  schedulerTime: string | null;
}