export interface Kpi {
  key: string;
  label: string;
  value: number;
  format: 'currency' | 'number' | 'percent';
  /** Variation en % — null si non applicable (la carte masque la flèche). */
  delta: number | null;
  icon: string;
  color: string;
}

export interface TimeSeriesPoint {
  label: string;
  value: number;
}

export interface CategorySlice {
  name: string;
  value: number;
}