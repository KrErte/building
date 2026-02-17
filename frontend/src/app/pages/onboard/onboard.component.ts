import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface OnboardingData {
  companyName: string;
  currentEmail: string;
  currentPhone: string;
  currentCategories: string[];
  currentServiceAreas: string[];
  alreadyOnboarded: boolean;
}

interface Category {
  id: string;
  label: string;
  selected: boolean;
}

interface ServiceArea {
  id: string;
  label: string;
  selected: boolean;
}

@Component({
  selector: 'app-onboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-violet-50 via-white to-indigo-50">
      <!-- Header -->
      <header class="bg-white/80 backdrop-blur-sm border-b border-gray-100">
        <div class="max-w-4xl mx-auto px-4 py-4">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 bg-gradient-to-br from-violet-500 to-indigo-600 rounded-xl flex items-center justify-center">
              <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"/>
              </svg>
            </div>
            <span class="text-xl font-bold bg-gradient-to-r from-violet-600 to-indigo-600 bg-clip-text text-transparent">
              BuildQuote
            </span>
          </div>
        </div>
      </header>

      <main class="max-w-2xl mx-auto px-4 py-8">
        <!-- Loading State -->
        @if (loading()) {
          <div class="flex flex-col items-center justify-center py-20">
            <div class="w-12 h-12 border-4 border-violet-200 border-t-violet-600 rounded-full animate-spin"></div>
            <p class="mt-4 text-gray-600">Laadin...</p>
          </div>
        }

        <!-- Error State -->
        @if (error()) {
          <div class="bg-white rounded-2xl shadow-xl p-8 text-center">
            <div class="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
              </svg>
            </div>
            <h2 class="text-xl font-semibold text-gray-800 mb-2">Link ei ole kehtiv</h2>
            <p class="text-gray-600">See registreerimislink on aegunud või vigane.</p>
          </div>
        }

        <!-- Success State -->
        @if (submitted()) {
          <div class="bg-white rounded-2xl shadow-xl p-8 text-center">
            <div class="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
              <svg class="w-10 h-10 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
              </svg>
            </div>
            <h2 class="text-2xl font-bold text-gray-800 mb-3">Aitäh!</h2>
            <p class="text-lg text-gray-600 mb-6">
              Teie ettevõte on registreeritud. Hakkate saama hinnapäringuid oma valdkonnas.
            </p>
            <div class="bg-violet-50 rounded-xl p-4 text-left">
              <h3 class="font-semibold text-violet-800 mb-2">Mis juhtub edasi?</h3>
              <ul class="text-violet-700 space-y-2">
                <li class="flex items-start gap-2">
                  <span class="text-violet-500 mt-0.5">✓</span>
                  <span>Saate hinnapäringuid otse oma e-postile</span>
                </li>
                <li class="flex items-start gap-2">
                  <span class="text-violet-500 mt-0.5">✓</span>
                  <span>Igale päringule saate vastata ühe klikiga</span>
                </li>
                <li class="flex items-start gap-2">
                  <span class="text-violet-500 mt-0.5">✓</span>
                  <span>Klient valib parima pakkumise</span>
                </li>
              </ul>
            </div>
          </div>
        }

        <!-- Already Onboarded State -->
        @if (data()?.alreadyOnboarded && !submitted()) {
          <div class="bg-white rounded-2xl shadow-xl p-8 text-center">
            <div class="w-16 h-16 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg class="w-8 h-8 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
            </div>
            <h2 class="text-xl font-semibold text-gray-800 mb-2">Juba registreeritud!</h2>
            <p class="text-gray-600">
              <strong>{{ data()?.companyName }}</strong> on juba BuildQuote platvormil registreeritud.
            </p>
          </div>
        }

        <!-- Registration Form -->
        @if (data() && !data()?.alreadyOnboarded && !submitted() && !loading() && !error()) {
          <div class="bg-white rounded-2xl shadow-xl overflow-hidden">
            <!-- Form Header -->
            <div class="bg-gradient-to-r from-violet-500 to-indigo-600 px-6 py-8 text-white text-center">
              <h1 class="text-2xl font-bold mb-2">Registreerige oma ettevõte</h1>
              <p class="opacity-90">BuildQuote platvormil</p>
            </div>

            <form (ngSubmit)="onSubmit()" class="p-6 space-y-6">
              <!-- Company Name -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">Ettevõtte nimi</label>
                <input
                  type="text"
                  [(ngModel)]="form.companyName"
                  name="companyName"
                  class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-violet-500 focus:border-transparent transition"
                  required
                />
              </div>

              <!-- Contact Person -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">Kontaktisiku nimi</label>
                <input
                  type="text"
                  [(ngModel)]="form.contactPerson"
                  name="contactPerson"
                  placeholder="Ees- ja perekonnanimi"
                  class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-violet-500 focus:border-transparent transition"
                  required
                />
              </div>

              <!-- Email & Phone -->
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">E-post</label>
                  <input
                    type="email"
                    [(ngModel)]="form.email"
                    name="email"
                    placeholder="info@firma.ee"
                    class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-violet-500 focus:border-transparent transition"
                    required
                  />
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-2">Telefon</label>
                  <input
                    type="tel"
                    [(ngModel)]="form.phone"
                    name="phone"
                    placeholder="+372 5XX XXXX"
                    class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-violet-500 focus:border-transparent transition"
                    required
                  />
                </div>
              </div>

              <!-- Categories -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-3">Tegevusvaldkonnad</label>
                <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
                  @for (cat of categories; track cat.id) {
                    <label
                      class="flex items-center gap-2 p-3 border rounded-xl cursor-pointer transition"
                      [class.border-violet-500]="cat.selected"
                      [class.bg-violet-50]="cat.selected"
                      [class.border-gray-200]="!cat.selected"
                    >
                      <input
                        type="checkbox"
                        [(ngModel)]="cat.selected"
                        [name]="'cat_' + cat.id"
                        class="w-4 h-4 text-violet-600 rounded focus:ring-violet-500"
                      />
                      <span class="text-sm" [class.text-violet-700]="cat.selected" [class.font-medium]="cat.selected">
                        {{ cat.label }}
                      </span>
                    </label>
                  }
                </div>
              </div>

              <!-- Service Areas -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-3">Teeninduspiirkonnad</label>
                <div class="grid grid-cols-2 md:grid-cols-3 gap-2">
                  @for (area of serviceAreas; track area.id) {
                    <label
                      class="flex items-center gap-2 p-3 border rounded-xl cursor-pointer transition"
                      [class.border-violet-500]="area.selected"
                      [class.bg-violet-50]="area.selected"
                      [class.border-gray-200]="!area.selected"
                    >
                      <input
                        type="checkbox"
                        [(ngModel)]="area.selected"
                        [name]="'area_' + area.id"
                        class="w-4 h-4 text-violet-600 rounded focus:ring-violet-500"
                      />
                      <span class="text-sm" [class.text-violet-700]="area.selected" [class.font-medium]="area.selected">
                        {{ area.label }}
                      </span>
                    </label>
                  }
                </div>
              </div>

              <!-- Additional Info -->
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">Lisainfo (valikuline)</label>
                <textarea
                  [(ngModel)]="form.additionalInfo"
                  name="additionalInfo"
                  rows="3"
                  placeholder="Lisage siia täiendav info oma ettevõtte kohta..."
                  class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-violet-500 focus:border-transparent transition resize-none"
                ></textarea>
              </div>

              <!-- Submit Button -->
              <button
                type="submit"
                [disabled]="submitting()"
                class="w-full py-4 bg-gradient-to-r from-violet-500 to-indigo-600 text-white font-semibold rounded-xl hover:from-violet-600 hover:to-indigo-700 focus:ring-4 focus:ring-violet-200 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                @if (submitting()) {
                  <span class="flex items-center justify-center gap-2">
                    <svg class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Registreerin...
                  </span>
                } @else {
                  Registreeri
                }
              </button>

              <p class="text-center text-sm text-gray-500">
                Registreerudes nõustute meie
                <a href="/terms" class="text-violet-600 hover:underline">kasutustingimustega</a>.
              </p>
            </form>
          </div>
        }
      </main>

      <!-- Footer -->
      <footer class="max-w-4xl mx-auto px-4 py-8 text-center text-sm text-gray-500">
        <p>© 2024 BuildQuote OÜ. Kõik õigused kaitstud.</p>
      </footer>
    </div>
  `,
  styles: []
})
export class OnboardComponent implements OnInit {
  loading = signal(true);
  error = signal(false);
  submitted = signal(false);
  submitting = signal(false);
  data = signal<OnboardingData | null>(null);

  token = '';

  form = {
    companyName: '',
    contactPerson: '',
    email: '',
    phone: '',
    additionalInfo: ''
  };

  categories: Category[] = [
    { id: 'GENERAL_CONSTRUCTION', label: 'Üldehitus', selected: false },
    { id: 'ELECTRICAL', label: 'Elektritööd', selected: false },
    { id: 'PLUMBING', label: 'Sanitaar', selected: false },
    { id: 'TILING', label: 'Plaatimine', selected: false },
    { id: 'FINISHING', label: 'Viimistlus', selected: false },
    { id: 'ROOFING', label: 'Katused', selected: false },
    { id: 'FACADE', label: 'Fassaad', selected: false },
    { id: 'DEMOLITION', label: 'Lammutus', selected: false },
    { id: 'LANDSCAPING', label: 'Maastikutööd', selected: false },
    { id: 'FLOORING', label: 'Põrandatööd', selected: false },
    { id: 'WINDOWS_DOORS', label: 'Aknad/Uksed', selected: false },
    { id: 'HVAC', label: 'Küte/Ventilatsioon', selected: false }
  ];

  serviceAreas: ServiceArea[] = [
    { id: 'TALLINN', label: 'Tallinn', selected: false },
    { id: 'TARTU', label: 'Tartu', selected: false },
    { id: 'PARNU', label: 'Pärnu', selected: false },
    { id: 'NARVA', label: 'Narva', selected: false },
    { id: 'HARJUMAA', label: 'Harjumaa', selected: false },
    { id: 'TARTUMAA', label: 'Tartumaa', selected: false },
    { id: 'PARNUMAA', label: 'Pärnumaa', selected: false },
    { id: 'IDA_VIRUMAA', label: 'Ida-Virumaa', selected: false },
    { id: 'NATIONWIDE', label: 'Üle-eestiline', selected: false }
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.token = this.route.snapshot.paramMap.get('token') || '';
    if (!this.token) {
      this.error.set(true);
      this.loading.set(false);
      return;
    }

    this.loadSupplierData();
  }

  loadSupplierData() {
    this.http.get<OnboardingData>(`${environment.apiUrl}/onboard/${this.token}`)
      .subscribe({
        next: (data) => {
          this.data.set(data);
          this.form.companyName = data.companyName || '';
          this.form.email = data.currentEmail || '';
          this.form.phone = data.currentPhone || '';

          // Pre-select current categories
          if (data.currentCategories) {
            this.categories.forEach(cat => {
              cat.selected = data.currentCategories.includes(cat.id);
            });
          }

          // Pre-select current service areas
          if (data.currentServiceAreas) {
            this.serviceAreas.forEach(area => {
              area.selected = data.currentServiceAreas.includes(area.id);
            });
          }

          this.loading.set(false);
        },
        error: (err) => {
          console.error('Error loading supplier data:', err);
          this.error.set(true);
          this.loading.set(false);
        }
      });
  }

  onSubmit() {
    const selectedCategories = this.categories.filter(c => c.selected).map(c => c.id);
    const selectedAreas = this.serviceAreas.filter(a => a.selected).map(a => a.id);

    if (selectedCategories.length === 0) {
      alert('Palun valige vähemalt üks tegevusvaldkond');
      return;
    }

    if (selectedAreas.length === 0) {
      alert('Palun valige vähemalt üks teeninduspiirkond');
      return;
    }

    this.submitting.set(true);

    const payload = {
      companyName: this.form.companyName,
      contactPerson: this.form.contactPerson,
      email: this.form.email,
      phone: this.form.phone,
      categories: selectedCategories,
      serviceAreas: selectedAreas,
      additionalInfo: this.form.additionalInfo
    };

    this.http.post(`${environment.apiUrl}/onboard/${this.token}`, payload)
      .subscribe({
        next: () => {
          this.submitted.set(true);
          this.submitting.set(false);
        },
        error: (err) => {
          console.error('Error submitting:', err);
          alert('Viga registreerimisel. Palun proovige uuesti.');
          this.submitting.set(false);
        }
      });
  }
}
