import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PipeLengthSummary } from '../../models/project.model';

@Component({
  selector: 'app-pipe-length-summary',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pipe-length-section">
      <div class="section-header">
        <div class="section-title-row">
          <h3 class="section-title">Torude pikkused</h3>
          @if (summaries && summaries.length > 0) {
            <button class="csv-export-btn" (click)="exportCsv()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              CSV
            </button>
          }
        </div>
        <p class="section-subtitle">Retseptide p&#245;hjal arvutatud torupikkused v&#228;rvi j&#228;rgi</p>
      </div>

      @if (!summaries || summaries.length === 0) {
        <div class="empty-state">
          <div class="empty-icon">&#x1F6BF;</div>
          <p>Torupikkusi ei leitud. Lisage torut&#246;&#246;de etappe.</p>
        </div>
      } @else {
        <!-- Color cards -->
        <div class="color-cards">
          @for (summary of summaries; track summary.color) {
            <div class="color-card" [class]="'card-' + summary.color.toLowerCase()">
              <div class="card-header">
                <div class="color-indicator" [class]="'indicator-' + summary.color.toLowerCase()"></div>
                <div class="card-title">
                  <span class="color-label">{{ summary.colorLabel }}</span>
                  <span class="color-desc">{{ summary.description }}</span>
                </div>
                <div class="total-length">
                  {{ summary.totalLengthM | number:'1.1-1' }} <span class="unit">jm</span>
                </div>
              </div>

              <div class="pipe-list">
                @for (pipe of summary.pipes; track pipe.name) {
                  <div class="pipe-item">
                    <div class="pipe-info">
                      <span class="pipe-name">{{ pipe.name }}</span>
                      @if (pipe.diameter) {
                        <span class="diameter-badge">{{ pipe.diameter }}</span>
                      }
                    </div>
                    <div class="pipe-length">{{ pipe.lengthM | number:'1.1-1' }} jm</div>
                    <div class="pipe-bar-wrapper">
                      <div class="pipe-bar" [class]="'bar-' + summary.color.toLowerCase()" [style.width.%]="getBarWidth(pipe.lengthM, summary.totalLengthM)"></div>
                    </div>
                    <div class="pipe-sources">
                      @for (stage of pipe.sourceStages; track stage) {
                        <span class="source-tag">{{ stage }}</span>
                      }
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        </div>

        <!-- Grand total bar -->
        <div class="grand-total-bar">
          <div class="total-segments">
            @for (summary of summaries; track summary.color) {
              <div
                class="segment"
                [class]="'seg-' + summary.color.toLowerCase()"
                [style.flex]="summary.totalLengthM"
                [title]="summary.colorLabel + ': ' + summary.totalLengthM + ' jm'"
              ></div>
            }
          </div>
          <div class="total-row">
            <span class="total-label">Torusid kokku</span>
            <span class="total-value">{{ grandTotal | number:'1.1-1' }} jm</span>
          </div>
          <div class="total-legend">
            @for (summary of summaries; track summary.color) {
              <span class="legend-item">
                <span class="legend-dot" [class]="'dot-' + summary.color.toLowerCase()"></span>
                {{ summary.colorLabel }}: {{ summary.totalLengthM | number:'1.1-1' }} jm
              </span>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .pipe-length-section {
      padding: 0;
    }

    .section-header {
      margin-bottom: 20px;
    }

    .section-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 4px;
    }

    .section-title {
      font-size: 1.1rem;
      font-weight: 600;
      color: #e2e8f0;
      margin: 0;
    }

    .section-subtitle {
      font-size: 0.85rem;
      color: #94a3b8;
      margin: 0;
    }

    .csv-export-btn {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 6px;
      color: #a5b4fc;
      font-size: 0.8rem;
      cursor: pointer;
      transition: all 0.2s;
    }

    .csv-export-btn:hover {
      background: rgba(99, 102, 241, 0.25);
    }

    .empty-state {
      text-align: center;
      padding: 40px 20px;
      color: #94a3b8;
    }

    .empty-icon {
      font-size: 2rem;
      margin-bottom: 12px;
    }

    .color-cards {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .color-card {
      border-radius: 12px;
      overflow: hidden;
      border: 1px solid rgba(255, 255, 255, 0.08);
    }

    .card-purple {
      background: rgba(147, 51, 234, 0.06);
      border-color: rgba(147, 51, 234, 0.2);
    }
    .card-brown {
      background: rgba(180, 120, 60, 0.06);
      border-color: rgba(180, 120, 60, 0.2);
    }
    .card-blue {
      background: rgba(59, 130, 246, 0.06);
      border-color: rgba(59, 130, 246, 0.2);
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 20px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    }

    .color-indicator {
      width: 6px;
      height: 40px;
      border-radius: 3px;
      flex-shrink: 0;
    }

    .indicator-purple { background: #a855f7; }
    .indicator-brown { background: #b47a3c; }
    .indicator-blue { background: #3b82f6; }

    .card-title {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    .color-label {
      font-size: 0.95rem;
      font-weight: 600;
      color: #e2e8f0;
    }

    .color-desc {
      font-size: 0.78rem;
      color: #94a3b8;
      margin-top: 2px;
    }

    .total-length {
      font-size: 1.3rem;
      font-weight: 700;
      color: #e2e8f0;
      flex-shrink: 0;
    }

    .total-length .unit {
      font-size: 0.8rem;
      font-weight: 400;
      color: #94a3b8;
    }

    .pipe-list {
      padding: 8px 20px 16px;
    }

    .pipe-item {
      padding: 8px 0;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
    }

    .pipe-item:last-child {
      border-bottom: none;
    }

    .pipe-info {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
    }

    .pipe-name {
      font-size: 0.88rem;
      color: #e2e8f0;
    }

    .diameter-badge {
      padding: 1px 6px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 4px;
      font-size: 0.72rem;
      color: #94a3b8;
      font-weight: 600;
    }

    .pipe-length {
      font-size: 0.85rem;
      font-weight: 600;
      color: #cbd5e1;
      margin-bottom: 4px;
    }

    .pipe-bar-wrapper {
      height: 6px;
      background: rgba(255, 255, 255, 0.06);
      border-radius: 3px;
      margin-bottom: 6px;
      overflow: hidden;
    }

    .pipe-bar {
      height: 100%;
      border-radius: 3px;
      transition: width 0.6s ease;
      min-width: 4px;
    }

    .bar-purple { background: linear-gradient(90deg, #a855f7, #c084fc); }
    .bar-brown { background: linear-gradient(90deg, #b47a3c, #d4a76a); }
    .bar-blue { background: linear-gradient(90deg, #3b82f6, #60a5fa); }

    .pipe-sources {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
    }

    .source-tag {
      padding: 1px 6px;
      background: rgba(99, 102, 241, 0.1);
      border: 1px solid rgba(99, 102, 241, 0.2);
      border-radius: 4px;
      font-size: 0.68rem;
      color: #a5b4fc;
      white-space: nowrap;
      max-width: 180px;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .grand-total-bar {
      margin-top: 20px;
      padding: 16px 20px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
    }

    .total-segments {
      display: flex;
      height: 10px;
      border-radius: 5px;
      overflow: hidden;
      gap: 2px;
      margin-bottom: 12px;
    }

    .segment {
      border-radius: 5px;
      min-width: 8px;
    }

    .seg-purple { background: linear-gradient(90deg, #a855f7, #c084fc); }
    .seg-brown { background: linear-gradient(90deg, #b47a3c, #d4a76a); }
    .seg-blue { background: linear-gradient(90deg, #3b82f6, #60a5fa); }

    .total-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .total-label {
      font-size: 0.95rem;
      font-weight: 600;
      color: #e2e8f0;
    }

    .total-value {
      font-size: 1.15rem;
      font-weight: 700;
      color: #a5b4fc;
    }

    .total-legend {
      display: flex;
      gap: 16px;
      margin-top: 10px;
      flex-wrap: wrap;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.78rem;
      color: #94a3b8;
    }

    .legend-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .dot-purple { background: #a855f7; }
    .dot-brown { background: #b47a3c; }
    .dot-blue { background: #3b82f6; }

    @media (max-width: 768px) {
      .card-header {
        padding: 12px 14px;
      }

      .pipe-list {
        padding: 8px 14px 12px;
      }

      .total-length {
        font-size: 1.1rem;
      }

      .total-legend {
        flex-direction: column;
        gap: 6px;
      }
    }
  `]
})
export class PipeLengthSummaryComponent {
  @Input() summaries: PipeLengthSummary[] = [];

  get grandTotal(): number {
    return this.summaries.reduce((sum, s) => sum + (s.totalLengthM || 0), 0);
  }

  getBarWidth(lengthM: number, totalLengthM: number): number {
    if (!totalLengthM || totalLengthM === 0) return 0;
    return Math.max(5, (lengthM / totalLengthM) * 100);
  }

  exportCsv(): void {
    if (!this.summaries || this.summaries.length === 0) return;

    const headers = ['Värv', 'Toru', 'Diameeter', 'Pikkus (jm)', 'Allikad'];
    const rows: string[][] = [];

    for (const summary of this.summaries) {
      for (const pipe of summary.pipes) {
        rows.push([
          summary.colorLabel,
          pipe.name,
          pipe.diameter || '',
          String(pipe.lengthM),
          (pipe.sourceStages || []).join('; ')
        ]);
      }
      // Add subtotal row
      rows.push([summary.colorLabel + ' KOKKU', '', '', String(summary.totalLengthM), '']);
    }
    // Grand total
    rows.push(['KOKKU', '', '', String(this.grandTotal), '']);

    const csv = [headers, ...rows].map(r => r.join(';')).join('\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'torude_pikkused.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}
