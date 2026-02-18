import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DependentMaterial } from '../../models/project.model';

@Component({
  selector: 'app-dependent-materials',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dependent-materials-section">
      <div class="section-header">
        <div class="section-title-row">
          <h3 class="section-title">Sõltuvad materjalid</h3>
          @if (materials && materials.length > 0) {
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
        <p class="section-subtitle">Komponentide retseptide põhjal arvutatud materjalid</p>
      </div>

      @if (!materials || materials.length === 0) {
        <div class="empty-state">
          <div class="empty-icon">📦</div>
          <p>Sõltuvaid materjale ei leitud. Retseptid ei vasta tuvastatud etappidele.</p>
        </div>
      } @else {
        <div class="materials-table">
          <div class="table-header-row">
            <span class="col-material">Materjal</span>
            <span class="col-qty">Kogus</span>
            <span class="col-unit">Ühik</span>
            <span class="col-unit-price">Ühiku hind</span>
            <span class="col-total">Kokku</span>
            <span class="col-source">Allikad</span>
          </div>

          @for (mat of materials; track mat.materialName) {
            <div class="table-row">
              <span class="col-material">{{ mat.materialName }}</span>
              <span class="col-qty">{{ mat.totalQuantity | number:'1.0-2' }}</span>
              <span class="col-unit">{{ mat.unit }}</span>
              <span class="col-unit-price">
                @if (mat.unitPriceMin && mat.unitPriceMax) {
                  {{ mat.unitPriceMin | number:'1.2-2' }}–{{ mat.unitPriceMax | number:'1.2-2' }} €
                } @else {
                  <span class="no-price">hind puudub</span>
                }
              </span>
              <span class="col-total">
                @if (mat.totalPriceMin && mat.totalPriceMax) {
                  {{ mat.totalPriceMin | number:'1.0-0' }}–{{ mat.totalPriceMax | number:'1.0-0' }} €
                } @else {
                  <span class="no-price">–</span>
                }
              </span>
              <span class="col-source">
                @for (stage of mat.sourceStages; track stage) {
                  <span class="source-badge">{{ stage }}</span>
                }
              </span>
            </div>
          }

          <div class="table-total-row">
            <span class="col-material total-label">Kokku</span>
            <span class="col-qty"></span>
            <span class="col-unit"></span>
            <span class="col-unit-price"></span>
            <span class="col-total total-value">
              {{ getTotalMin() | number:'1.0-0' }}–{{ getTotalMax() | number:'1.0-0' }} €
            </span>
            <span class="col-source"></span>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .dependent-materials-section {
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

    .materials-table {
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      overflow: hidden;
    }

    .table-header-row {
      display: grid;
      grid-template-columns: 2fr 0.8fr 0.6fr 1.2fr 1.2fr 1.5fr;
      padding: 12px 16px;
      background: rgba(255, 255, 255, 0.04);
      border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      font-size: 0.78rem;
      font-weight: 600;
      color: #94a3b8;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .table-row {
      display: grid;
      grid-template-columns: 2fr 0.8fr 0.6fr 1.2fr 1.2fr 1.5fr;
      padding: 10px 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      font-size: 0.88rem;
      color: #e2e8f0;
      align-items: center;
    }

    .table-row:hover {
      background: rgba(255, 255, 255, 0.03);
    }

    .table-total-row {
      display: grid;
      grid-template-columns: 2fr 0.8fr 0.6fr 1.2fr 1.2fr 1.5fr;
      padding: 14px 16px;
      background: rgba(99, 102, 241, 0.08);
      border-top: 1px solid rgba(99, 102, 241, 0.2);
      font-weight: 600;
    }

    .total-label {
      color: #a5b4fc;
      font-size: 0.9rem;
    }

    .total-value {
      color: #a5b4fc;
      font-size: 0.9rem;
    }

    .no-price {
      color: #f59e0b;
      font-size: 0.8rem;
      font-style: italic;
    }

    .source-badge {
      display: inline-block;
      padding: 2px 8px;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.25);
      border-radius: 4px;
      font-size: 0.72rem;
      color: #a5b4fc;
      margin: 1px 3px 1px 0;
      white-space: nowrap;
      max-width: 180px;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .col-source {
      display: flex;
      flex-wrap: wrap;
      gap: 2px;
    }

    @media (max-width: 768px) {
      .table-header-row,
      .table-row,
      .table-total-row {
        grid-template-columns: 1fr 0.6fr 0.5fr 1fr;
      }

      .col-source,
      .col-unit-price {
        display: none;
      }
    }
  `]
})
export class DependentMaterialsComponent {
  @Input() materials: DependentMaterial[] = [];

  getTotalMin(): number {
    return this.materials.reduce((sum, m) => sum + (m.totalPriceMin || 0), 0);
  }

  getTotalMax(): number {
    return this.materials.reduce((sum, m) => sum + (m.totalPriceMax || 0), 0);
  }

  exportCsv(): void {
    if (!this.materials || this.materials.length === 0) return;

    const headers = ['Materjal', 'Kogus', 'Ühik', 'Min hind/ühik', 'Max hind/ühik', 'Min kokku', 'Max kokku', 'Allikad'];
    const rows = this.materials.map(m => [
      m.materialName,
      m.totalQuantity,
      m.unit,
      m.unitPriceMin || '',
      m.unitPriceMax || '',
      m.totalPriceMin || '',
      m.totalPriceMax || '',
      (m.sourceStages || []).join('; ')
    ]);

    const csv = [headers, ...rows].map(r => r.join(',')).join('\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'materjalid.csv';
    a.click();
    URL.revokeObjectURL(url);
  }
}
