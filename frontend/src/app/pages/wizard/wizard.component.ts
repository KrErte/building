import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../services/auth.service';
import { MarketPriceEstimate, WizardSupplier, RfqRequest, Campaign, Bid } from '../../models/rfq.model';

@Component({
  selector: 'app-wizard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './wizard.component.html',
  styleUrl: './wizard.component.scss'
})
export class WizardComponent implements OnInit {
  private apiUrl = environment.apiUrl;

  currentStep = signal(1);
  loading = signal(false);
  error = signal<string | null>(null);

  // Step 1 - Form data
  formData = signal({
    title: '',
    description: '',
    category: '',
    location: '',
    area: null as number | null,
    budgetMin: null as number | null,
    budgetMax: null as number | null,
    deadline: ''
  });

  // Market price estimate
  priceEstimate = signal<MarketPriceEstimate | null>(null);
  loadingPrice = signal(false);

  // Step 2 - Suppliers
  suppliers = signal<WizardSupplier[]>([]);
  loadingSuppliers = signal(false);
  selectedSuppliers = computed(() => this.suppliers().filter(s => s.selected));

  // Step 3 - Campaign and bids
  campaign = signal<Campaign | null>(null);
  loadingCampaign = signal(false);

  // Options
  categories = [
    { value: 'TILING', label: 'Plaatimine' },
    { value: 'ELECTRICAL', label: 'Elektritööd' },
    { value: 'PLUMBING', label: 'Sanitaartehnika' },
    { value: 'FINISHING', label: 'Viimistlus' },
    { value: 'FLOORING', label: 'Põrandatööd' },
    { value: 'ROOFING', label: 'Katuse tööd' },
    { value: 'HVAC', label: 'Küte ja ventilatsioon' },
    { value: 'WINDOWS_DOORS', label: 'Aknad ja uksed' },
    { value: 'FACADE', label: 'Fassaaditööd' },
    { value: 'DEMOLITION', label: 'Lammutustööd' },
    { value: 'LANDSCAPING', label: 'Haljastus' },
    { value: 'GENERAL_CONSTRUCTION', label: 'Üldehitus' }
  ];

  locations = [
    { value: 'Tallinn', label: 'Tallinn' },
    { value: 'Tartu', label: 'Tartu' },
    { value: 'Pärnu', label: 'Pärnu' },
    { value: 'Narva', label: 'Narva' },
    { value: 'Viljandi', label: 'Viljandi' },
    { value: 'Rakvere', label: 'Rakvere' },
    { value: 'Kuressaare', label: 'Kuressaare' },
    { value: 'Haapsalu', label: 'Haapsalu' },
    { value: 'Harjumaa', label: 'Harjumaa' },
    { value: 'Pärnumaa', label: 'Pärnumaa' },
    { value: 'Tartumaa', label: 'Tartumaa' }
  ];

  steps = [
    { number: 1, title: 'Kirjelda tööd' },
    { number: 2, title: 'Vali tarnijad' },
    { number: 3, title: 'Vaata pakkumisi' }
  ];

  constructor(
    private http: HttpClient,
    private router: Router,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    // Set default deadline to 2 weeks from now
    const defaultDeadline = new Date();
    defaultDeadline.setDate(defaultDeadline.getDate() + 14);
    this.formData.update(data => ({
      ...data,
      deadline: defaultDeadline.toISOString().split('T')[0]
    }));
  }

  updateFormField(field: string, value: any): void {
    this.formData.update(data => ({ ...data, [field]: value }));

    // Fetch price estimate when category and area are set
    if ((field === 'category' || field === 'area') && this.formData().category && this.formData().area) {
      this.fetchPriceEstimate();
    }
  }

  fetchPriceEstimate(): void {
    const { category, area, location } = this.formData();
    if (!category || !area) return;

    this.loadingPrice.set(true);
    this.http.get<MarketPriceEstimate>(`${this.apiUrl}/prices/check`, {
      params: {
        category,
        quantity: area.toString(),
        region: location || 'Tallinn'
      }
    }).subscribe({
      next: (estimate) => {
        this.priceEstimate.set(estimate);
        this.loadingPrice.set(false);
      },
      error: () => {
        this.loadingPrice.set(false);
      }
    });
  }

  isStep1Valid(): boolean {
    const data = this.formData();
    return !!(data.title && data.category && data.location && data.area && data.deadline);
  }

  nextStep(): void {
    if (this.currentStep() === 1) {
      if (!this.isStep1Valid()) {
        this.error.set('Palun täida kõik kohustuslikud väljad');
        return;
      }
      this.error.set(null);
      this.currentStep.set(2);
      this.loadSuppliers();
    } else if (this.currentStep() === 2) {
      if (this.selectedSuppliers().length === 0) {
        this.error.set('Palun vali vähemalt üks tarnija');
        return;
      }
      this.error.set(null);
      this.sendRfq();
    }
  }

  prevStep(): void {
    if (this.currentStep() > 1) {
      this.currentStep.update(s => s - 1);
      this.error.set(null);
    }
  }

  cancel(): void {
    if (confirm('Kas oled kindel, et soovid tühistada?')) {
      this.router.navigate(['/']);
    }
  }

  loadSuppliers(): void {
    const { category, location } = this.formData();
    this.loadingSuppliers.set(true);

    this.http.get<any>(`${this.apiUrl}/companies`, {
      params: {
        size: '50',
        category: category || '',
        city: location || ''
      }
    }).subscribe({
      next: (response) => {
        const suppliers = (response.companies || response.content || response || []).map((s: any) => ({
          ...s,
          selected: false
        }));
        this.suppliers.set(suppliers);
        this.loadingSuppliers.set(false);
      },
      error: () => {
        this.loadingSuppliers.set(false);
        this.error.set('Tarnijate laadimine ebaõnnestus');
      }
    });
  }

  toggleSupplier(supplier: WizardSupplier): void {
    this.suppliers.update(list =>
      list.map(s => s.id === supplier.id ? { ...s, selected: !s.selected } : s)
    );
  }

  selectAllSuppliers(): void {
    this.suppliers.update(list => list.map(s => ({ ...s, selected: true })));
  }

  deselectAllSuppliers(): void {
    this.suppliers.update(list => list.map(s => ({ ...s, selected: false })));
  }

  sendRfq(): void {
    this.loading.set(true);
    const data = this.formData();
    const selected = this.selectedSuppliers();

    const request: RfqRequest = {
      title: data.title,
      category: data.category,
      location: data.location,
      quantity: data.area || 0,
      unit: 'm²',
      specifications: data.description,
      maxBudget: data.budgetMax,
      deadline: data.deadline,
      supplierIds: selected.map(s => s.id)
    };

    this.http.post<Campaign>(`${this.apiUrl}/rfq/send`, request).subscribe({
      next: (campaign) => {
        this.campaign.set(campaign);
        this.currentStep.set(3);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Hinnapäringu saatmine ebaõnnestus');
      }
    });
  }

  getCategoryLabel(value: string): string {
    return this.categories.find(c => c.value === value)?.label || value;
  }

  getBidStatusClass(status: string): string {
    switch (status) {
      case 'SUBMITTED': return 'status-received';
      case 'ACCEPTED': return 'status-accepted';
      case 'REJECTED': return 'status-rejected';
      default: return 'status-pending';
    }
  }

  getBidStatusLabel(status: string): string {
    switch (status) {
      case 'SUBMITTED': return 'Saabunud';
      case 'ACCEPTED': return 'Aktsepteeritud';
      case 'REJECTED': return 'Tagasi lükatud';
      default: return 'Ootel';
    }
  }

  formatPrice(price: number): string {
    return new Intl.NumberFormat('et-EE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(price);
  }

  goToProjects(): void {
    this.router.navigate(['/projects/new']);
  }
}
