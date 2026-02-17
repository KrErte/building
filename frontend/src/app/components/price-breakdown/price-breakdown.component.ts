import { Component, Input, Output, EventEmitter, signal, computed, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PriceBreakdownService } from '../../services/price-breakdown.service';
import { PriceBreakdown, MaterialLine, SupplierPrice } from '../../models/price-breakdown.model';

@Component({
  selector: 'app-price-breakdown',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Loading State -->
    @if (loading()) {
      <div class="breakdown-loading">
        <div class="skeleton-section">
          <div class="skeleton-header"></div>
          <div class="skeleton-table">
            <div class="skeleton-row"></div>
            <div class="skeleton-row"></div>
            <div class="skeleton-row"></div>
          </div>
        </div>
        <div class="skeleton-section">
          <div class="skeleton-header short"></div>
          <div class="skeleton-row wide"></div>
        </div>
        <div class="loading-text">
          <span class="spinner"></span>
          Laadin hinnaandmeid...
        </div>
      </div>
    }

    <!-- Breakdown Content -->
    @if (!loading() && breakdown()) {
      <div class="price-breakdown" [@fadeIn]>
        <!-- Materials Section -->
        <div class="breakdown-section">
          <h4 class="section-title">
            <span class="title-icon">📦</span>
            MATERJALID
          </h4>

          <!-- Desktop Table -->
          <div class="materials-table desktop-only">
            <div class="table-header">
              <span class="col-name">Materjal</span>
              <span class="col-qty">Kogus</span>
              <span class="col-unit">Ühik</span>
              <span class="col-price">Ühikuhind</span>
              <span class="col-source">Allikas</span>
              <span class="col-total">Kokku</span>
            </div>
            @for (material of editableMaterials(); track material.name) {
              <div class="table-row">
                <span class="col-name">{{ material.name }}</span>
                <span class="col-qty">
                  @if (editMode()) {
                    <input
                      type="number"
                      [value]="material.quantity"
                      (input)="updateQuantity(material, $event)"
                      class="qty-input"
                      min="0"
                      step="0.1"
                    >
                  } @else {
                    {{ formatNumber(material.quantity) }}
                  }
                </span>
                <span class="col-unit">{{ material.unit }}</span>
                <span class="col-price">{{ formatCurrency(material.unitPriceMin) }}–{{ formatCurrency(material.unitPriceMax) }}</span>
                <span class="col-source">
                  <a
                    [href]="material.supplierUrl"
                    target="_blank"
                    class="supplier-link"
                    (click)="openSupplierModal(material, $event)"
                  >
                    {{ material.supplierName }}
                    <span class="link-icon">▪</span>
                  </a>
                  <span class="source-badge" [class.auto]="material.priceSource === 'AUTO'">
                    {{ material.priceSource === 'AUTO' ? 'Automaatne' : 'Käsitsi' }}
                  </span>
                  <span class="updated-date">Uuendatud: {{ formatDate(material.lastUpdated) }}</span>
                </span>
                <span class="col-total">{{ formatCurrency(getMaterialTotal(material).min) }}–{{ formatCurrency(getMaterialTotal(material).max) }}</span>
              </div>
            }
          </div>

          <!-- Mobile Cards -->
          <div class="materials-cards mobile-only">
            @for (material of editableMaterials(); track material.name) {
              <div class="material-card">
                <div class="card-header">
                  <span class="material-name">{{ material.name }}</span>
                  <span class="material-total">{{ formatCurrency(getMaterialTotal(material).min) }}–{{ formatCurrency(getMaterialTotal(material).max) }}</span>
                </div>
                <div class="card-details">
                  <div class="detail-row">
                    <span class="label">Kogus:</span>
                    @if (editMode()) {
                      <input
                        type="number"
                        [value]="material.quantity"
                        (input)="updateQuantity(material, $event)"
                        class="qty-input"
                      >
                    } @else {
                      <span>{{ formatNumber(material.quantity) }} {{ material.unit }}</span>
                    }
                  </div>
                  <div class="detail-row">
                    <span class="label">Ühikuhind:</span>
                    <span>{{ formatCurrency(material.unitPriceMin) }}–{{ formatCurrency(material.unitPriceMax) }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="label">Allikas:</span>
                    <a
                      [href]="material.supplierUrl"
                      target="_blank"
                      class="supplier-link"
                      (click)="openSupplierModal(material, $event)"
                    >
                      {{ material.supplierName }} ▪
                    </a>
                  </div>
                  <div class="badges-row">
                    <span class="source-badge" [class.auto]="material.priceSource === 'AUTO'">
                      {{ material.priceSource === 'AUTO' ? 'Automaatne' : 'Käsitsi' }}
                    </span>
                    <span class="updated-date">{{ formatDate(material.lastUpdated) }}</span>
                  </div>
                </div>
              </div>
            }
          </div>
        </div>

        <!-- Labor Section -->
        <div class="breakdown-section">
          <h4 class="section-title">
            <span class="title-icon">👷</span>
            TÖÖJÕUD
          </h4>
          <div class="labor-content">
            <div class="labor-calculation">
              ~{{ formatNumber(breakdown()!.labor.hoursEstimate) }}h ×
              {{ formatCurrency(breakdown()!.labor.hourlyRateMin) }}–{{ formatCurrency(breakdown()!.labor.hourlyRateMax) }}/h =
              <strong>{{ formatCurrency(breakdown()!.labor.totalMin) }} — {{ formatCurrency(breakdown()!.labor.totalMax) }}</strong>
            </div>
            <div class="labor-source">
              Allikas: {{ breakdown()!.labor.source }}
            </div>
          </div>
        </div>

        <!-- Summary Section -->
        <div class="breakdown-section summary-section">
          <div class="summary-header">
            <h4 class="section-title">
              <span class="title-icon">📊</span>
              KOKKUVÕTE
            </h4>
            <div class="confidence-indicator" [class]="getConfidenceClass()">
              <span class="confidence-dot"></span>
              TÄPSUS: {{ breakdown()!.confidencePercent }}%
              <span class="confidence-label">{{ breakdown()!.confidenceLabel }}</span>
            </div>
          </div>

          <div class="summary-box">
            <div class="summary-row">
              <span class="summary-label">Materjalid:</span>
              <span class="summary-value">{{ formatCurrency(getMaterialsTotal().min) }} — {{ formatCurrency(getMaterialsTotal().max) }}</span>
            </div>
            <div class="summary-row">
              <span class="summary-label">Tööjõud:</span>
              <span class="summary-value">{{ formatCurrency(breakdown()!.labor.totalMin) }} — {{ formatCurrency(breakdown()!.labor.totalMax) }}</span>
            </div>
            <div class="summary-row">
              <span class="summary-label">Muud kulud:</span>
              <span class="summary-value">{{ formatCurrency(breakdown()!.otherCosts.totalMin) }} — {{ formatCurrency(breakdown()!.otherCosts.totalMax) }}</span>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-row total">
              <span class="summary-label">KOKKU:</span>
              <span class="summary-value">{{ formatCurrency(getCalculatedTotal().min) }} — {{ formatCurrency(getCalculatedTotal().max) }}</span>
            </div>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="breakdown-actions">
          <button class="action-btn secondary" (click)="openSupplierComparison()">
            <span class="btn-icon">🔍</span>
            Võrdle tarnijaid
          </button>
          <button class="action-btn secondary" (click)="toggleEditMode()">
            <span class="btn-icon">✏️</span>
            {{ editMode() ? 'Lõpeta muutmine' : 'Muuda koguseid' }}
          </button>
          @if (editMode() && hasChanges()) {
            <button class="action-btn primary" (click)="saveChanges()">
              <span class="btn-icon">💾</span>
              Salvesta muudatused
            </button>
          }
          <button class="action-btn secondary" (click)="exportPdf()">
            <span class="btn-icon">📄</span>
            PDF eksport
          </button>
        </div>
      </div>
    }

    <!-- Supplier Comparison Modal -->
    @if (showSupplierModal()) {
      <div class="modal-overlay" (click)="closeSupplierModal()">
        <div class="modal-content supplier-modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ selectedMaterial()?.name }} — Hinnavõrdlus</h3>
            <button class="close-btn" (click)="closeSupplierModal()">×</button>
          </div>
          <div class="modal-body">
            @if (loadingSuppliers()) {
              <div class="loading-suppliers">
                <span class="spinner"></span>
                Laadin tarnijate hindasid...
              </div>
            } @else {
              <div class="supplier-list">
                @for (supplier of supplierPrices(); track supplier.supplierName; let i = $index) {
                  <div class="supplier-item" [class.best]="i === 0">
                    @if (i === 0) {
                      <span class="best-badge">Parim hind</span>
                    }
                    <div class="supplier-info">
                      <span class="supplier-name">{{ supplier.supplierName }}</span>
                      <span class="supplier-updated">Uuendatud: {{ formatDate(supplier.lastUpdated) }}</span>
                    </div>
                    <div class="supplier-price">{{ formatCurrency(supplier.price) }}/{{ selectedMaterial()?.unit }}</div>
                    <a [href]="supplier.url" target="_blank" class="supplier-link-btn">
                      Vaata →
                    </a>
                  </div>
                }
              </div>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .price-breakdown {
      padding: 1.5rem 0 0;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
      margin-top: 1rem;
    }

    .breakdown-section {
      margin-bottom: 1.5rem;
    }

    .section-title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.1em;
      color: rgba(255, 255, 255, 0.7);
      margin-bottom: 0.75rem;
    }

    .title-icon {
      font-size: 1rem;
    }

    /* Materials Table */
    .materials-table {
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      overflow: hidden;
    }

    .table-header {
      display: grid;
      grid-template-columns: 2fr 1fr 0.7fr 1.2fr 2fr 1.2fr;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      background: rgba(0, 0, 0, 0.3);
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.05em;
      color: rgba(255, 255, 255, 0.5);
      text-transform: uppercase;
    }

    .table-row {
      display: grid;
      grid-template-columns: 2fr 1fr 0.7fr 1.2fr 2fr 1.2fr;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      font-size: 0.85rem;
      align-items: center;
    }

    .table-row:last-child {
      border-bottom: none;
    }

    .col-source {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .supplier-link {
      color: #a78bfa;
      text-decoration: none;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }

    .supplier-link:hover {
      color: #c4b5fd;
      text-decoration: underline;
    }

    .link-icon {
      font-size: 0.6rem;
    }

    .source-badge {
      display: inline-block;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      font-size: 0.65rem;
      font-weight: 500;
      background: rgba(255, 255, 255, 0.1);
      color: rgba(255, 255, 255, 0.6);
      width: fit-content;
    }

    .source-badge.auto {
      background: rgba(34, 197, 94, 0.2);
      color: #4ade80;
    }

    .updated-date {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.4);
    }

    .col-total {
      font-weight: 600;
      text-align: right;
    }

    .qty-input {
      width: 70px;
      padding: 0.25rem 0.5rem;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 4px;
      color: white;
      font-size: 0.85rem;
    }

    .qty-input:focus {
      outline: none;
      border-color: #a78bfa;
      background: rgba(167, 139, 250, 0.1);
    }

    /* Mobile Cards */
    .materials-cards {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .material-card {
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      padding: 1rem;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
    }

    .material-name {
      font-weight: 600;
    }

    .material-total {
      font-weight: 600;
      color: #a78bfa;
    }

    .card-details {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }

    .detail-row {
      display: flex;
      justify-content: space-between;
      font-size: 0.85rem;
    }

    .detail-row .label {
      color: rgba(255, 255, 255, 0.5);
    }

    .badges-row {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.25rem;
    }

    /* Labor Section */
    .labor-content {
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      padding: 1rem;
    }

    .labor-calculation {
      font-size: 0.95rem;
      margin-bottom: 0.5rem;
    }

    .labor-source {
      font-size: 0.8rem;
      color: rgba(255, 255, 255, 0.5);
    }

    /* Summary Section */
    .summary-section .summary-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }

    .confidence-indicator {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 0.4rem 0.75rem;
      border-radius: 20px;
      background: rgba(0, 0, 0, 0.3);
    }

    .confidence-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: currentColor;
    }

    .confidence-indicator.high {
      color: #4ade80;
    }

    .confidence-indicator.medium {
      color: #facc15;
    }

    .confidence-indicator.low {
      color: #f87171;
    }

    .confidence-label {
      font-weight: 400;
      opacity: 0.8;
      margin-left: 0.25rem;
    }

    .summary-box {
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      padding: 1rem;
    }

    .summary-row {
      display: flex;
      justify-content: space-between;
      padding: 0.4rem 0;
      font-size: 0.9rem;
    }

    .summary-label {
      color: rgba(255, 255, 255, 0.7);
    }

    .summary-value {
      font-weight: 500;
    }

    .summary-divider {
      height: 1px;
      background: rgba(255, 255, 255, 0.1);
      margin: 0.5rem 0;
    }

    .summary-row.total {
      font-size: 1.1rem;
      font-weight: 700;
    }

    .summary-row.total .summary-value {
      color: #a78bfa;
    }

    /* Action Buttons */
    .breakdown-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      margin-top: 1.5rem;
      padding-top: 1rem;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
    }

    .action-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.6rem 1rem;
      border-radius: 8px;
      font-size: 0.85rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
      border: none;
    }

    .action-btn.secondary {
      background: rgba(255, 255, 255, 0.1);
      color: white;
    }

    .action-btn.secondary:hover {
      background: rgba(255, 255, 255, 0.15);
    }

    .action-btn.primary {
      background: linear-gradient(135deg, #a78bfa, #8b5cf6);
      color: white;
    }

    .action-btn.primary:hover {
      background: linear-gradient(135deg, #c4b5fd, #a78bfa);
    }

    .btn-icon {
      font-size: 1rem;
    }

    /* Loading State */
    .breakdown-loading {
      padding: 1.5rem 0;
    }

    .skeleton-section {
      margin-bottom: 1.5rem;
    }

    .skeleton-header {
      height: 14px;
      width: 100px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 4px;
      margin-bottom: 0.75rem;
      animation: pulse 1.5s infinite;
    }

    .skeleton-header.short {
      width: 80px;
    }

    .skeleton-table {
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      padding: 1rem;
    }

    .skeleton-row {
      height: 40px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 4px;
      margin-bottom: 0.5rem;
      animation: pulse 1.5s infinite;
    }

    .skeleton-row.wide {
      height: 60px;
    }

    .skeleton-row:last-child {
      margin-bottom: 0;
    }

    .loading-text {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      padding: 1rem;
      font-size: 0.9rem;
      color: rgba(255, 255, 255, 0.6);
    }

    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid rgba(255, 255, 255, 0.2);
      border-top-color: #a78bfa;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Supplier Modal */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }

    .modal-content {
      background: #1e1e2e;
      border-radius: 12px;
      max-width: 500px;
      width: 100%;
      max-height: 80vh;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }

    .modal-header h3 {
      font-size: 1rem;
      font-weight: 600;
    }

    .close-btn {
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(255, 255, 255, 0.1);
      border: none;
      border-radius: 6px;
      color: white;
      font-size: 1.25rem;
      cursor: pointer;
    }

    .close-btn:hover {
      background: rgba(255, 255, 255, 0.15);
    }

    .modal-body {
      padding: 1rem 1.25rem;
      overflow-y: auto;
    }

    .loading-suppliers {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      padding: 2rem;
      color: rgba(255, 255, 255, 0.6);
    }

    .supplier-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .supplier-item {
      display: grid;
      grid-template-columns: 1fr auto auto;
      gap: 1rem;
      align-items: center;
      padding: 0.75rem;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      position: relative;
    }

    .supplier-item.best {
      background: rgba(34, 197, 94, 0.1);
      border: 1px solid rgba(34, 197, 94, 0.3);
    }

    .best-badge {
      position: absolute;
      top: -8px;
      left: 10px;
      padding: 0.15rem 0.5rem;
      background: #22c55e;
      color: black;
      font-size: 0.65rem;
      font-weight: 600;
      border-radius: 4px;
    }

    .supplier-info {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .supplier-name {
      font-weight: 500;
    }

    .supplier-updated {
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.4);
    }

    .supplier-price {
      font-weight: 600;
      font-size: 1.1rem;
    }

    .supplier-link-btn {
      padding: 0.4rem 0.75rem;
      background: rgba(167, 139, 250, 0.2);
      color: #a78bfa;
      border-radius: 6px;
      text-decoration: none;
      font-size: 0.85rem;
      font-weight: 500;
    }

    .supplier-link-btn:hover {
      background: rgba(167, 139, 250, 0.3);
    }

    /* Responsive */
    .desktop-only {
      display: block;
    }

    .mobile-only {
      display: none;
    }

    @media (max-width: 768px) {
      .desktop-only {
        display: none;
      }

      .mobile-only {
        display: flex;
      }

      .summary-section .summary-header {
        flex-direction: column;
        align-items: flex-start;
      }

      .breakdown-actions {
        flex-direction: column;
      }

      .action-btn {
        width: 100%;
        justify-content: center;
      }
    }
  `]
})
export class PriceBreakdownComponent implements OnInit, OnChanges {
  @Input() category = '';
  @Input() quantity = 0;
  @Input() unit = 'm2';
  @Input() autoLoad = true;

  @Output() breakdownLoaded = new EventEmitter<PriceBreakdown>();
  @Output() quantitiesChanged = new EventEmitter<MaterialLine[]>();

  loading = signal(false);
  breakdown = signal<PriceBreakdown | null>(null);
  editMode = signal(false);
  editableMaterials = signal<MaterialLine[]>([]);
  originalMaterials = signal<MaterialLine[]>([]);

  // Supplier modal
  showSupplierModal = signal(false);
  selectedMaterial = signal<MaterialLine | null>(null);
  supplierPrices = signal<SupplierPrice[]>([]);
  loadingSuppliers = signal(false);

  constructor(private priceBreakdownService: PriceBreakdownService) {}

  ngOnInit(): void {
    if (this.autoLoad && this.category && this.quantity > 0) {
      this.loadBreakdown();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['category'] || changes['quantity']) && this.autoLoad) {
      if (this.category && this.quantity > 0) {
        this.loadBreakdown();
      }
    }
  }

  loadBreakdown(): void {
    this.loading.set(true);
    this.priceBreakdownService.getPriceBreakdown(this.category, this.quantity, this.unit)
      .subscribe({
        next: (data) => {
          this.breakdown.set(data);
          this.editableMaterials.set([...data.materials]);
          this.originalMaterials.set([...data.materials]);
          this.loading.set(false);
          this.breakdownLoaded.emit(data);
        },
        error: () => {
          this.loading.set(false);
        }
      });
  }

  toggleEditMode(): void {
    this.editMode.update(v => !v);
    if (!this.editMode()) {
      // Reset to original values if canceling edit
      this.editableMaterials.set([...this.originalMaterials()]);
    }
  }

  updateQuantity(material: MaterialLine, event: Event): void {
    const input = event.target as HTMLInputElement;
    const newQty = parseFloat(input.value) || 0;

    this.editableMaterials.update(materials =>
      materials.map(m =>
        m.name === material.name ? { ...m, quantity: newQty } : m
      )
    );
  }

  hasChanges(): boolean {
    const current = this.editableMaterials();
    const original = this.originalMaterials();
    return current.some((m, i) => m.quantity !== original[i]?.quantity);
  }

  saveChanges(): void {
    this.originalMaterials.set([...this.editableMaterials()]);
    this.editMode.set(false);
    this.quantitiesChanged.emit(this.editableMaterials());
  }

  getMaterialTotal(material: MaterialLine): { min: number; max: number } {
    return {
      min: material.quantity * material.unitPriceMin,
      max: material.quantity * material.unitPriceMax
    };
  }

  getMaterialsTotal(): { min: number; max: number } {
    const materials = this.editableMaterials();
    return {
      min: materials.reduce((sum, m) => sum + m.quantity * m.unitPriceMin, 0),
      max: materials.reduce((sum, m) => sum + m.quantity * m.unitPriceMax, 0)
    };
  }

  getCalculatedTotal(): { min: number; max: number } {
    const materials = this.getMaterialsTotal();
    const bd = this.breakdown();
    if (!bd) return { min: 0, max: 0 };

    return {
      min: materials.min + bd.labor.totalMin + bd.otherCosts.totalMin,
      max: materials.max + bd.labor.totalMax + bd.otherCosts.totalMax
    };
  }

  getConfidenceClass(): string {
    const confidence = this.breakdown()?.confidencePercent || 0;
    if (confidence >= 80) return 'high';
    if (confidence >= 50) return 'medium';
    return 'low';
  }

  openSupplierModal(material: MaterialLine, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.selectedMaterial.set(material);
    this.showSupplierModal.set(true);
    this.loadSupplierPrices(material.name);
  }

  openSupplierComparison(): void {
    const materials = this.editableMaterials();
    if (materials.length > 0) {
      this.selectedMaterial.set(materials[0]);
      this.showSupplierModal.set(true);
      this.loadSupplierPrices(materials[0].name);
    }
  }

  loadSupplierPrices(materialName: string): void {
    this.loadingSuppliers.set(true);
    this.priceBreakdownService.getSupplierPrices(materialName)
      .subscribe({
        next: (prices) => {
          this.supplierPrices.set(prices);
          this.loadingSuppliers.set(false);
        },
        error: () => {
          this.loadingSuppliers.set(false);
        }
      });
  }

  closeSupplierModal(): void {
    this.showSupplierModal.set(false);
    this.selectedMaterial.set(null);
    this.supplierPrices.set([]);
  }

  exportPdf(): void {
    // TODO: Implement PDF export
    alert('PDF eksport tuleb peagi!');
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('et-EE', {
      style: 'decimal',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value) + ' €';
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat('et-EE', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 1
    }).format(value);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('et-EE', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }
}
