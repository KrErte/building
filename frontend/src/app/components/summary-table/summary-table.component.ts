import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectStage, DependentMaterial } from '../../models/project.model';

@Component({
  selector: 'app-summary-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="summary-table-section">
      <div class="section-header">
        <h3 class="section-title">Koondtabel</h3>
        <p class="section-subtitle">Tööd ja materjalid koos</p>
      </div>

      <div class="summary-table">
        <!-- Work section -->
        <div class="section-divider">
          <span class="divider-icon">🔨</span>
          <span class="divider-label">Tööd</span>
          <span class="divider-count">{{ stages.length }} etappi</span>
        </div>

        <div class="table-header-row">
          <span class="col-name">Nimetus</span>
          <span class="col-qty">Kogus</span>
          <span class="col-unit">Ühik</span>
          <span class="col-price">Hinnanguline maksumus</span>
        </div>

        @for (stage of stages; track stage.name) {
          <div class="table-row work-row">
            <span class="col-name">{{ stage.name }}</span>
            <span class="col-qty">{{ stage.quantity | number:'1.0-1' }}</span>
            <span class="col-unit">{{ stage.unit }}</span>
            <span class="col-price">
              @if (stage.priceEstimateMin && stage.priceEstimateMax) {
                {{ stage.priceEstimateMin | number:'1.0-0' }}–{{ stage.priceEstimateMax | number:'1.0-0' }} €
              } @else {
                –
              }
            </span>
          </div>
        }

        <div class="subtotal-row">
          <span class="col-name subtotal-label">Tööde kokku</span>
          <span class="col-qty"></span>
          <span class="col-unit"></span>
          <span class="col-price subtotal-value">
            {{ getWorkTotalMin() | number:'1.0-0' }}–{{ getWorkTotalMax() | number:'1.0-0' }} €
          </span>
        </div>

        <!-- Materials section -->
        @if (materials && materials.length > 0) {
          <div class="section-divider">
            <span class="divider-icon">📦</span>
            <span class="divider-label">Materjalid</span>
            <span class="divider-count">{{ materials.length }} materjali</span>
          </div>

          @for (mat of materials; track mat.materialName) {
            <div class="table-row material-row">
              <span class="col-name">{{ mat.materialName }}</span>
              <span class="col-qty">{{ mat.totalQuantity | number:'1.0-2' }}</span>
              <span class="col-unit">{{ mat.unit }}</span>
              <span class="col-price">
                @if (mat.totalPriceMin && mat.totalPriceMax) {
                  {{ mat.totalPriceMin | number:'1.0-0' }}–{{ mat.totalPriceMax | number:'1.0-0' }} €
                } @else {
                  <span class="no-price">hind puudub</span>
                }
              </span>
            </div>
          }

          <div class="subtotal-row">
            <span class="col-name subtotal-label">Materjalide kokku</span>
            <span class="col-qty"></span>
            <span class="col-unit"></span>
            <span class="col-price subtotal-value">
              {{ getMaterialsTotalMin() | number:'1.0-0' }}–{{ getMaterialsTotalMax() | number:'1.0-0' }} €
            </span>
          </div>
        }

        <!-- Grand total -->
        <div class="grand-total-row">
          <span class="col-name grand-label">KOKKU</span>
          <span class="col-qty"></span>
          <span class="col-unit"></span>
          <span class="col-price grand-value">
            {{ getGrandTotalMin() | number:'1.0-0' }}–{{ getGrandTotalMax() | number:'1.0-0' }} €
          </span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .summary-table-section {
      padding: 0;
    }

    .section-header {
      margin-bottom: 20px;
    }

    .section-title {
      font-size: 1.1rem;
      font-weight: 600;
      color: #e2e8f0;
      margin: 0 0 4px 0;
    }

    .section-subtitle {
      font-size: 0.85rem;
      color: #94a3b8;
      margin: 0;
    }

    .summary-table {
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      overflow: hidden;
    }

    .section-divider {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 16px;
      background: rgba(99, 102, 241, 0.06);
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    }

    .divider-icon {
      font-size: 1rem;
    }

    .divider-label {
      font-weight: 600;
      color: #a5b4fc;
      font-size: 0.85rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .divider-count {
      color: #64748b;
      font-size: 0.78rem;
      margin-left: auto;
    }

    .table-header-row {
      display: grid;
      grid-template-columns: 2.5fr 0.8fr 0.6fr 1.5fr;
      padding: 10px 16px;
      background: rgba(255, 255, 255, 0.03);
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .table-row {
      display: grid;
      grid-template-columns: 2.5fr 0.8fr 0.6fr 1.5fr;
      padding: 10px 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
      font-size: 0.88rem;
      color: #e2e8f0;
      align-items: center;
    }

    .table-row:hover {
      background: rgba(255, 255, 255, 0.02);
    }

    .material-row {
      color: #cbd5e1;
    }

    .subtotal-row {
      display: grid;
      grid-template-columns: 2.5fr 0.8fr 0.6fr 1.5fr;
      padding: 10px 16px;
      background: rgba(255, 255, 255, 0.03);
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    }

    .subtotal-label {
      font-weight: 600;
      color: #94a3b8;
      font-size: 0.85rem;
    }

    .subtotal-value {
      font-weight: 600;
      color: #94a3b8;
      font-size: 0.85rem;
    }

    .grand-total-row {
      display: grid;
      grid-template-columns: 2.5fr 0.8fr 0.6fr 1.5fr;
      padding: 14px 16px;
      background: rgba(99, 102, 241, 0.1);
      border-top: 2px solid rgba(99, 102, 241, 0.3);
    }

    .grand-label {
      font-weight: 700;
      color: #a5b4fc;
      font-size: 0.95rem;
    }

    .grand-value {
      font-weight: 700;
      color: #a5b4fc;
      font-size: 0.95rem;
    }

    .no-price {
      color: #f59e0b;
      font-size: 0.8rem;
      font-style: italic;
    }

    @media (max-width: 768px) {
      .table-header-row,
      .table-row,
      .subtotal-row,
      .grand-total-row {
        grid-template-columns: 1.5fr 0.6fr 0.5fr 1fr;
      }
    }
  `]
})
export class SummaryTableComponent {
  @Input() stages: ProjectStage[] = [];
  @Input() materials: DependentMaterial[] = [];

  getWorkTotalMin(): number {
    return this.stages.reduce((sum, s) => sum + (s.priceEstimateMin || 0), 0);
  }

  getWorkTotalMax(): number {
    return this.stages.reduce((sum, s) => sum + (s.priceEstimateMax || 0), 0);
  }

  getMaterialsTotalMin(): number {
    return this.materials.reduce((sum, m) => sum + (m.totalPriceMin || 0), 0);
  }

  getMaterialsTotalMax(): number {
    return this.materials.reduce((sum, m) => sum + (m.totalPriceMax || 0), 0);
  }

  getGrandTotalMin(): number {
    return this.getWorkTotalMin() + this.getMaterialsTotalMin();
  }

  getGrandTotalMax(): number {
    return this.getWorkTotalMax() + this.getMaterialsTotalMax();
  }
}
