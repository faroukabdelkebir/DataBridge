import { Injectable } from '@angular/core';
import { jsPDF } from 'jspdf';

import { QualityReport } from '../models/ai-agents.model';

const DIMENSION_LABELS: Record<string, string> = {
  D1: 'Complétude', D2: 'Unicité', D3: 'Couverture schéma',
  D4: 'Conformité', D5: 'Fraîcheur', D6: 'Consistance',
};

/** Génère un PDF vectoriel (texte sélectionnable) à partir d'un rapport qualité. */
@Injectable({ providedIn: 'root' })
export class QualityPdfService {

  /** Construit le document et le renvoie sous forme de Blob (pour viewer + téléchargement). */
  build(report: QualityReport): Blob {
    const doc = new jsPDF({ unit: 'pt', format: 'a4' });
    const pageW = doc.internal.pageSize.getWidth();
    const pageH = doc.internal.pageSize.getHeight();
    const margin = 48;
    const contentW = pageW - margin * 2;
    let y = margin;

    const ensureSpace = (needed: number) => {
      if (y + needed > pageH - margin) { doc.addPage(); y = margin; }
    };

    // ── En-tête ────────────────────────────────────────────────
    doc.setFillColor(37, 99, 235);
    doc.rect(0, 0, pageW, 90, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(20);
    doc.text('Rapport de qualité des données', margin, 46);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(11);
    const subtitle = [
      report.table ? `Table : ${report.table}` : null,
      `Généré le ${new Date().toLocaleString('fr-FR')}`,
    ].filter(Boolean).join('   •   ');
    doc.text(subtitle, margin, 68);
    y = 120;

    // ── Score global + grade ───────────────────────────────────
    const score = report.global_score ?? 0;
    const grade = report.grade ?? '?';
    const scoreColor = this.scoreRgb(score);

    doc.setTextColor(...scoreColor);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(40);
    doc.text(this.fmt(score, 1), margin, y + 30);
    doc.setFontSize(13);
    doc.setTextColor(60, 60, 60);
    doc.text(`/ 100   —   Grade ${grade}`, margin + 90, y + 30);
    y += 60;

    // ── Dimensions ─────────────────────────────────────────────
    const dims = report.dimensions ?? {};
    const dimEntries = Object.entries(dims);
    if (dimEntries.length) {
      this.sectionTitle(doc, 'Dimensions', margin, y);
      y += 24;

      for (const [key, v] of dimEntries) {
        ensureSpace(28);
        const label = `${key} · ${DIMENSION_LABELS[key] ?? key}`;
        const s = v.score ?? 0;
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(10);
        doc.setTextColor(40, 40, 40);
        doc.text(label, margin, y + 9);

        // Barre de progression
        const barX = margin + 170;
        const barW = contentW - 170 - 44;
        doc.setFillColor(230, 230, 230);
        doc.roundedRect(barX, y, barW, 10, 3, 3, 'F');
        doc.setFillColor(...this.scoreRgb(s));
        doc.roundedRect(barX, y, Math.max(1, (barW * Math.min(100, s)) / 100), 10, 3, 3, 'F');

        doc.setTextColor(60, 60, 60);
        doc.text(this.fmt(s, 0), barX + barW + 10, y + 9);
        y += 20;
      }
      y += 10;
    }

    // ── Interprétation ─────────────────────────────────────────
    if (report.interpretation) {
      ensureSpace(48);
      this.sectionTitle(doc, 'Interprétation', margin, y);
      y += 22;
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(10);
      doc.setTextColor(40, 40, 40);
      const lines = doc.splitTextToSize(report.interpretation, contentW) as string[];
      for (const line of lines) {
        ensureSpace(14);
        doc.text(line, margin, y);
        y += 14;
      }
      y += 12;
    }

    // ── Actions d'amélioration ─────────────────────────────────
    const improvements = report.improvements_by_impact ?? [];
    if (improvements.length) {
      ensureSpace(48);
      this.sectionTitle(doc, "Actions d'amélioration", margin, y);
      y += 22;

      // En-têtes du tableau
      const cols = [
        { label: 'Dimension', x: margin, w: 90 },
        { label: 'Actuel', x: margin + 90, w: 55 },
        { label: 'Gain max', x: margin + 145, w: 60 },
        { label: 'Action', x: margin + 205, w: contentW - 205 },
      ];
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9);
      doc.setTextColor(90, 90, 90);
      for (const c of cols) doc.text(c.label, c.x, y);
      y += 6;
      doc.setDrawColor(210, 210, 210);
      doc.line(margin, y, margin + contentW, y);
      y += 12;

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(9);
      for (const a of improvements) {
        const actionLines = doc.splitTextToSize(a.action ?? '', cols[3].w) as string[];
        const rowH = Math.max(14, actionLines.length * 12);
        ensureSpace(rowH + 4);
        doc.setTextColor(40, 40, 40);
        doc.text(String(a.dimension ?? ''), cols[0].x, y);
        doc.text(this.fmt(a.current ?? 0, 1), cols[1].x, y);
        doc.setTextColor(22, 163, 74);
        doc.text('+' + this.fmt(a.max_gain ?? 0, 1), cols[2].x, y);
        doc.setTextColor(40, 40, 40);
        doc.text(actionLines, cols[3].x, y);
        y += rowH + 4;
      }
    }

    // ── Pied de page (numéros) ─────────────────────────────────
    const pages = doc.getNumberOfPages();
    for (let i = 1; i <= pages; i++) {
      doc.setPage(i);
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      doc.setTextColor(150, 150, 150);
      doc.text(`Page ${i} / ${pages}`, pageW - margin, pageH - 24, { align: 'right' });
    }

    return doc.output('blob');
  }

  /** Nom de fichier proposé au téléchargement. */
  fileName(report: QualityReport): string {
    const table = (report.table ?? 'rapport').replace(/[^\w.-]+/g, '_');
    const date = new Date().toISOString().slice(0, 10);
    return `qualite_${table}_${date}.pdf`;
  }

  // ── Helpers ──────────────────────────────────────────────────
  private sectionTitle(doc: jsPDF, text: string, x: number, y: number): void {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(13);
    doc.setTextColor(30, 30, 30);
    doc.text(text, x, y);
  }

  private scoreRgb(score: number): [number, number, number] {
    if (score >= 80) return [34, 197, 94];
    if (score >= 50) return [245, 158, 11];
    return [239, 68, 68];
  }

  private fmt(n: number, decimals: number): string {
    return Number(n).toFixed(decimals);
  }
}
