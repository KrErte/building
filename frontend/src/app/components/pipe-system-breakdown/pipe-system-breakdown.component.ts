import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PipeSystem, PipeComponent, PIPE_SYSTEM_ICONS } from '../../models/project.model';

@Component({
  selector: 'app-pipe-system-breakdown',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pipe-systems-section">
      <div class="section-header">
        <div class="section-title-row">
          <h3 class="section-title">Torustiku süsteemid</h3>
          <button class="csv-export-btn" (click)="exportCsv()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            CSV
          </button>
        </div>
        <p class="section-subtitle">AI tuvastas {{ pipeSystems.length }} torustiku süsteemi jooniselt</p>
      </div>

      @for (system of pipeSystems; track system.systemCode; let i = $index) {
        <div class="system-card" [class.expanded]="system.expanded">
          <div class="system-header" (click)="toggleSystem(system)">
            <div class="system-icon">{{ getSystemIcon(system.systemCode) }}</div>
            <div class="system-info">
              <div class="system-name-row">
                <span class="system-name">{{ system.systemName }}</span>
                <span class="system-code-badge">{{ system.systemCode }}</span>
              </div>
              <div class="system-meta">
                <span class="system-length">{{ system.lengthMeters | number:'1.1-1' }} m</span>
                <span class="meta-sep">|</span>
                <span class="system-specs">{{ system.pipeSpecs }}</span>
                <span class="meta-sep">|</span>
                <span class="system-components">{{ system.components.length }} komponenti</span>
              </div>
            </div>
            <div class="system-right">
              <div class="system-price">
                {{ formatCurrency(system.totalPriceMin) }} — {{ formatCurrency(system.totalPriceMax) }}
              </div>
              <div class="confidence-badge" [class.high]="system.confidence >= 0.7" [class.medium]="system.confidence >= 0.4 && system.confidence < 0.7" [class.low]="system.confidence < 0.4">
                {{ (system.confidence * 100) | number:'1.0-0' }}%
              </div>
            </div>
            <div class="expand-arrow" [class.expanded]="system.expanded">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </div>
          </div>

          @if (system.expanded) {
            <div class="system-details">
              <table class="bom-table">
                <thead>
                  <tr>
                    <th>Komponent</th>
                    <th>Diameeter</th>
                    <th>Kogus</th>
                    <th>Pikkus</th>
                    <th>Ühiku hind</th>
                    <th>Kokku</th>
                  </tr>
                </thead>
                <tbody>
                  @for (comp of system.components; track $index) {
                    <tr>
                      <td class="comp-name">
                        <span class="comp-type-icon">{{ getComponentIcon(comp.type) }}</span>
                        {{ comp.typeLabel }}
                        @if (comp.material) {
                          <span class="material-badge">{{ comp.material }}</span>
                        }
                      </td>
                      <td>
                        @if (comp.diameterMm > 0) {
                          DN{{ comp.diameterMm }}
                        } @else {
                          —
                        }
                      </td>
                      <td>{{ comp.count }} {{ comp.priceUnit }}</td>
                      <td>
                        @if (comp.lengthM) {
                          {{ comp.lengthM | number:'1.1-1' }} m
                        } @else {
                          —
                        }
                      </td>
                      <td class="price-cell">
                        {{ formatCurrency(comp.unitPriceMin) }} — {{ formatCurrency(comp.unitPriceMax) }}
                        <span class="price-unit">/{{ comp.priceUnit }}</span>
                      </td>
                      <td class="price-cell total">
                        {{ formatCurrency(comp.totalPriceMin) }} — {{ formatCurrency(comp.totalPriceMax) }}
                      </td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr>
                    <td colspan="5" class="total-label">Süsteemi kokku</td>
                    <td class="price-cell total">
                      {{ formatCurrency(system.totalPriceMin) }} — {{ formatCurrency(system.totalPriceMax) }}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          }
        </div>
      }

      <!-- Grand Total -->
      @if (pipeSystems.length > 1) {
        <div class="grand-total">
          <span class="grand-total-label">Torustiku kogumaht</span>
          <span class="grand-total-price">
            {{ formatCurrency(grandTotalMin) }} — {{ formatCurrency(grandTotalMax) }}
          </span>
        </div>
      }
    </div>
  `,
  styles: [`
    .pipe-systems-section {
      margin: 1.5rem 0;
    }

    .section-header {
      margin-bottom: 1rem;
    }

    .section-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }

    .section-title {
      font-size: 1.2rem;
      font-weight: 600;
      color: #e2e8f0;
      margin: 0;
    }

    .section-subtitle {
      font-size: 0.85rem;
      color: #94a3b8;
      margin: 0.25rem 0 0;
    }

    .csv-export-btn {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.4rem 0.8rem;
      background: rgba(99, 102, 241, 0.15);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 0.5rem;
      color: #a5b4fc;
      font-size: 0.8rem;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        background: rgba(99, 102, 241, 0.25);
        color: #c7d2fe;
      }
    }

    .system-card {
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 0.75rem;
      margin-bottom: 0.75rem;
      overflow: hidden;
      transition: all 0.3s;

      &:hover {
        border-color: rgba(99, 102, 241, 0.3);
      }

      &.expanded {
        border-color: rgba(99, 102, 241, 0.4);
        background: rgba(255, 255, 255, 0.06);
      }
    }

    .system-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem 1.25rem;
      cursor: pointer;
      user-select: none;
    }

    .system-icon {
      font-size: 1.5rem;
      width: 2.5rem;
      height: 2.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(99, 102, 241, 0.1);
      border-radius: 0.5rem;
      flex-shrink: 0;
    }

    .system-info {
      flex: 1;
      min-width: 0;
    }

    .system-name-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .system-name {
      font-size: 0.95rem;
      font-weight: 600;
      color: #e2e8f0;
    }

    .system-code-badge {
      padding: 0.1rem 0.5rem;
      background: rgba(99, 102, 241, 0.2);
      border-radius: 0.25rem;
      font-size: 0.7rem;
      font-weight: 700;
      color: #a5b4fc;
      letter-spacing: 0.05em;
    }

    .system-meta {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.8rem;
      color: #94a3b8;
      margin-top: 0.25rem;
    }

    .meta-sep {
      color: rgba(148, 163, 184, 0.3);
    }

    .system-right {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 0.25rem;
      flex-shrink: 0;
    }

    .system-price {
      font-size: 0.9rem;
      font-weight: 600;
      color: #a5b4fc;
    }

    .confidence-badge {
      padding: 0.1rem 0.4rem;
      border-radius: 0.25rem;
      font-size: 0.7rem;
      font-weight: 600;

      &.high {
        background: rgba(34, 197, 94, 0.15);
        color: #4ade80;
      }
      &.medium {
        background: rgba(234, 179, 8, 0.15);
        color: #fbbf24;
      }
      &.low {
        background: rgba(239, 68, 68, 0.15);
        color: #f87171;
      }
    }

    .expand-arrow {
      color: #64748b;
      transition: transform 0.3s;
      flex-shrink: 0;

      &.expanded {
        transform: rotate(180deg);
      }
    }

    .system-details {
      padding: 0 1.25rem 1.25rem;
      animation: slideDown 0.3s ease;
    }

    @keyframes slideDown {
      from { opacity: 0; max-height: 0; }
      to { opacity: 1; max-height: 500px; }
    }

    .bom-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;

      th {
        text-align: left;
        padding: 0.5rem 0.75rem;
        color: #94a3b8;
        font-weight: 500;
        font-size: 0.75rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        border-bottom: 1px solid rgba(255, 255, 255, 0.08);
      }

      td {
        padding: 0.5rem 0.75rem;
        color: #cbd5e1;
        border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      }

      tbody tr:hover td {
        background: rgba(99, 102, 241, 0.05);
      }

      tfoot td {
        font-weight: 600;
        color: #e2e8f0;
        border-top: 1px solid rgba(255, 255, 255, 0.1);
        border-bottom: none;
        padding-top: 0.75rem;
      }
    }

    .comp-name {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .comp-type-icon {
      font-size: 0.9rem;
    }

    .material-badge {
      padding: 0 0.3rem;
      background: rgba(255, 255, 255, 0.06);
      border-radius: 0.2rem;
      font-size: 0.7rem;
      color: #94a3b8;
    }

    .price-cell {
      text-align: right;
      white-space: nowrap;
    }

    .price-unit {
      color: #64748b;
      font-size: 0.7rem;
    }

    .total-label {
      text-align: right;
    }

    .grand-total {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      background: rgba(99, 102, 241, 0.08);
      border: 1px solid rgba(99, 102, 241, 0.2);
      border-radius: 0.75rem;
      margin-top: 0.5rem;
    }

    .grand-total-label {
      font-size: 0.95rem;
      font-weight: 600;
      color: #e2e8f0;
    }

    .grand-total-price {
      font-size: 1.1rem;
      font-weight: 700;
      color: #a5b4fc;
    }

    @media (max-width: 768px) {
      .system-header {
        flex-wrap: wrap;
        gap: 0.5rem;
      }

      .system-right {
        width: 100%;
        flex-direction: row;
        justify-content: space-between;
        margin-top: 0.25rem;
        padding-left: 3.25rem;
      }

      .bom-table {
        font-size: 0.75rem;

        th, td {
          padding: 0.4rem 0.5rem;
        }
      }

      .system-meta {
        flex-wrap: wrap;
      }
    }
  `]
})
export class PipeSystemBreakdownComponent {
  @Input() pipeSystems: PipeSystem[] = [];

  systemIcons = PIPE_SYSTEM_ICONS;

  get grandTotalMin(): number {
    return this.pipeSystems.reduce((sum, s) => sum + (s.totalPriceMin || 0), 0);
  }

  get grandTotalMax(): number {
    return this.pipeSystems.reduce((sum, s) => sum + (s.totalPriceMax || 0), 0);
  }

  toggleSystem(system: PipeSystem): void {
    system.expanded = !system.expanded;
  }

  getSystemIcon(code: string): string {
    return this.systemIcons[code] || '🔧';
  }

  getComponentIcon(type: string): string {
    switch (type) {
      case 'STRAIGHT': return '📏';
      case 'ELBOW_90': return '↪️';
      case 'ELBOW_45': return '↗️';
      case 'TEE': return '⊤';
      case 'COUPLING': return '🔗';
      case 'REDUCER': return '🔽';
      case 'VALVE': return '🔶';
      case 'MANHOLE': return '⭕';
      case 'DRAIN': return '⬇️';
      case 'CLEANOUT': return '🔘';
      default: return '•';
    }
  }

  formatCurrency(value: number): string {
    if (!value && value !== 0) return '—';
    return new Intl.NumberFormat('et-EE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  }

  exportCsv(): void {
    const BOM = '\uFEFF';
    const SEP = ';';
    const lines: string[] = [];

    lines.push(['Süsteem', 'Kood', 'Komponent', 'Diameeter', 'Kogus', 'Ühik', 'Pikkus (m)', 'Materjal', 'Min hind', 'Max hind'].join(SEP));

    for (const sys of this.pipeSystems) {
      for (const comp of sys.components) {
        lines.push([
          sys.systemName,
          sys.systemCode,
          comp.typeLabel,
          comp.diameterMm > 0 ? `DN${comp.diameterMm}` : '',
          String(comp.count),
          comp.priceUnit,
          comp.lengthM ? String(comp.lengthM) : '',
          comp.material || '',
          comp.totalPriceMin ? String(comp.totalPriceMin) : '',
          comp.totalPriceMax ? String(comp.totalPriceMax) : ''
        ].join(SEP));
      }
    }

    const csvContent = BOM + lines.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'torustiku_bom.csv';
    link.click();
    URL.revokeObjectURL(url);
  }
}
