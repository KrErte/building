import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface OnboardingStats {
  total: number;
  withEmail: number;
  withToken: number;
  emailsSent: number;
  onboarded: number;
  eligibleToSend: number;
  conversionRate: string;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="min-h-screen bg-gray-50">
      <!-- Header -->
      <header class="bg-white border-b border-gray-200">
        <div class="max-w-6xl mx-auto px-4 py-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 bg-gradient-to-br from-violet-500 to-indigo-600 rounded-xl flex items-center justify-center">
                <span class="text-white text-xl">&#9889;</span>
              </div>
              <div>
                <span class="text-xl font-bold text-gray-900">BuildQuote Admin</span>
                <span class="ml-2 text-sm text-gray-500">Onboarding</span>
              </div>
            </div>
            <a href="/" class="text-violet-600 hover:text-violet-700">&#8592; Back to site</a>
          </div>
        </div>
      </header>

      <main class="max-w-6xl mx-auto px-4 py-8">
        <!-- Stats Cards -->
        @if (stats()) {
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            <div class="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <div class="text-3xl font-bold text-gray-900">{{ stats()?.total }}</div>
              <div class="text-sm text-gray-500">Kokku firmasid</div>
            </div>
            <div class="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <div class="text-3xl font-bold text-violet-600">{{ stats()?.eligibleToSend }}</div>
              <div class="text-sm text-gray-500">Valmis saatmiseks</div>
            </div>
            <div class="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <div class="text-3xl font-bold text-blue-600">{{ stats()?.emailsSent }}</div>
              <div class="text-sm text-gray-500">Emaile saadetud</div>
            </div>
            <div class="bg-white rounded-xl p-6 shadow-sm border border-gray-100">
              <div class="text-3xl font-bold text-green-600">{{ stats()?.onboarded }}</div>
              <div class="text-sm text-gray-500">Registreerunud</div>
            </div>
          </div>
        }

        <!-- Onboarding Section -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
          <h2 class="text-xl font-semibold text-gray-900 mb-4">Onboarding Campaign</h2>

          <div class="bg-violet-50 rounded-lg p-4 mb-6">
            <div class="flex items-center gap-2 mb-2">
              <span class="text-2xl">&#128231;</span>
              <span class="text-lg font-semibold text-violet-800">
                {{ stats()?.eligibleToSend || 0 }} firmade emailid valmis saatmiseks
              </span>
            </div>
            <p class="text-violet-700 text-sm">
              Emailid saadetakse firmadele, kellel on email ja token, aga pole veel emaili saadetud.
            </p>
          </div>

          <div class="flex flex-wrap gap-3">
            <!-- Dry Run Button -->
            <button
              (click)="dryRun()"
              [disabled]="sending()"
              class="px-6 py-3 bg-gray-100 text-gray-700 font-medium rounded-lg hover:bg-gray-200 transition disabled:opacity-50"
            >
              @if (sending() && sendMode() === 'dryrun') {
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  Kontrollin...
                </span>
              } @else {
                &#128269; Dry Run (ei saada)
              }
            </button>

            <!-- Test Email Button -->
            <button
              (click)="sendTest()"
              [disabled]="sending()"
              class="px-6 py-3 bg-blue-500 text-white font-medium rounded-lg hover:bg-blue-600 transition disabled:opacity-50"
            >
              @if (sending() && sendMode() === 'test') {
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  Saadan...
                </span>
              } @else {
                &#9993; Testi (1 email)
              }
            </button>

            <!-- Mass Send Button -->
            <button
              (click)="confirmMassSend()"
              [disabled]="sending() || stats()?.eligibleToSend === 0"
              class="px-6 py-3 bg-green-500 text-white font-medium rounded-lg hover:bg-green-600 transition disabled:opacity-50"
            >
              @if (sending() && sendMode() === 'mass') {
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  Saadan...
                </span>
              } @else {
                &#128640; Saada koigile ({{ stats()?.eligibleToSend || 0 }})
              }
            </button>

            <!-- Generate Tokens Button -->
            <button
              (click)="generateTokens()"
              [disabled]="sending()"
              class="px-6 py-3 bg-orange-500 text-white font-medium rounded-lg hover:bg-orange-600 transition disabled:opacity-50"
            >
              @if (sending() && sendMode() === 'tokens') {
                <span class="flex items-center gap-2">
                  <svg class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
                  </svg>
                  Genereerin...
                </span>
              } @else {
                &#128273; Genereeri tokenid
              }
            </button>
          </div>
        </div>

        <!-- Results Section -->
        @if (result()) {
          <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <h3 class="text-lg font-semibold text-gray-900 mb-4">Tulemus</h3>
            <pre class="bg-gray-50 p-4 rounded-lg text-sm overflow-auto">{{ result() | json }}</pre>
          </div>
        }

        <!-- Confirmation Modal -->
        @if (showConfirm()) {
          <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div class="bg-white rounded-xl p-6 max-w-md mx-4">
              <h3 class="text-xl font-semibold text-gray-900 mb-4">&#9888; Kinnita saatmine</h3>
              <p class="text-gray-600 mb-6">
                Oled saatmas <strong>{{ stats()?.eligibleToSend }}</strong> emaili.
                Kas oled kindel?
              </p>
              <div class="flex gap-3">
                <button
                  (click)="showConfirm.set(false)"
                  class="flex-1 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition"
                >
                  Tuhista
                </button>
                <button
                  (click)="massSend()"
                  class="flex-1 px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 transition"
                >
                  Jah, saada!
                </button>
              </div>
            </div>
          </div>
        }
      </main>
    </div>
  `,
  styles: []
})
export class AdminComponent implements OnInit {
  stats = signal<OnboardingStats | null>(null);
  sending = signal(false);
  sendMode = signal<'dryrun' | 'test' | 'mass' | 'tokens' | null>(null);
  result = signal<any>(null);
  showConfirm = signal(false);

  private testEmail = 'kristo.erte@gmail.com';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadStats();
  }

  loadStats() {
    this.http.get<OnboardingStats>(`${environment.apiUrl}/onboard/admin/stats`)
      .subscribe({
        next: (data) => this.stats.set(data),
        error: (err) => console.error('Failed to load stats:', err)
      });
  }

  dryRun() {
    this.sending.set(true);
    this.sendMode.set('dryrun');
    this.result.set(null);

    this.http.post(`${environment.apiUrl}/onboard/mass-send`, { dryRun: true })
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.sending.set(false);
        },
        error: (err) => {
          this.result.set({ error: err.message });
          this.sending.set(false);
        }
      });
  }

  sendTest() {
    this.sending.set(true);
    this.sendMode.set('test');
    this.result.set(null);

    this.http.post(`${environment.apiUrl}/onboard/mass-send`, { testEmail: this.testEmail })
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.sending.set(false);
          this.loadStats();
        },
        error: (err) => {
          this.result.set({ error: err.message });
          this.sending.set(false);
        }
      });
  }

  confirmMassSend() {
    this.showConfirm.set(true);
  }

  massSend() {
    this.showConfirm.set(false);
    this.sending.set(true);
    this.sendMode.set('mass');
    this.result.set(null);

    this.http.post(`${environment.apiUrl}/onboard/mass-send`, { dryRun: false })
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.sending.set(false);
          this.loadStats();
        },
        error: (err) => {
          this.result.set({ error: err.message });
          this.sending.set(false);
        }
      });
  }

  generateTokens() {
    this.sending.set(true);
    this.sendMode.set('tokens');
    this.result.set(null);

    this.http.post(`${environment.apiUrl}/onboard/admin/generate-tokens`, {})
      .subscribe({
        next: (res) => {
          this.result.set(res);
          this.sending.set(false);
          this.loadStats();
        },
        error: (err) => {
          this.result.set({ error: err.message });
          this.sending.set(false);
        }
      });
  }
}
