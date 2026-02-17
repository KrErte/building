import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  template: `
    <div class="min-h-screen bg-gray-50">
      <div class="max-w-2xl mx-auto px-4 py-8">
        <a routerLink="/projects" class="text-violet-600 hover:text-violet-700 flex items-center gap-2 mb-6">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
          Tagasi
        </a>

        <h1 class="text-2xl font-bold text-gray-900 mb-6">Seaded</h1>

        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <!-- Language Section -->
          <div class="p-6 border-b border-gray-100">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Keel / Language / Язык</h2>
            <div class="flex gap-2">
              @for (lang of languages; track lang.code) {
                <button
                  (click)="setLanguage(lang.code)"
                  class="px-4 py-2 rounded-lg font-medium transition"
                  [class.bg-violet-500]="currentLang() === lang.code"
                  [class.text-white]="currentLang() === lang.code"
                  [class.bg-gray-100]="currentLang() !== lang.code"
                  [class.text-gray-700]="currentLang() !== lang.code"
                  [class.hover:bg-gray-200]="currentLang() !== lang.code"
                >
                  {{ lang.flag }} {{ lang.name }}
                </button>
              }
            </div>
          </div>

          <!-- Notifications Section -->
          <div class="p-6 border-b border-gray-100">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Teavitused</h2>
            <div class="space-y-3">
              <label class="flex items-center gap-3 cursor-pointer">
                <input type="checkbox" checked class="w-5 h-5 text-violet-600 rounded focus:ring-violet-500">
                <span class="text-gray-700">E-posti teavitused uutest pakkumistest</span>
              </label>
              <label class="flex items-center gap-3 cursor-pointer">
                <input type="checkbox" checked class="w-5 h-5 text-violet-600 rounded focus:ring-violet-500">
                <span class="text-gray-700">Iganädalane kokkuvõte</span>
              </label>
            </div>
          </div>

          <!-- Account Actions -->
          <div class="p-6">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Konto</h2>
            <div class="space-y-3">
              <a routerLink="/account" class="block w-full py-3 bg-gray-100 text-gray-700 font-medium rounded-lg hover:bg-gray-200 transition text-center">
                Muuda profiili
              </a>
              <button class="w-full py-3 bg-red-50 text-red-600 font-medium rounded-lg hover:bg-red-100 transition">
                Kustuta konto
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SettingsComponent {
  currentLang = signal('et');

  languages = [
    { code: 'et', name: 'Eesti', flag: '🇪🇪' },
    { code: 'en', name: 'English', flag: '🇬🇧' },
    { code: 'ru', name: 'Русский', flag: '🇷🇺' }
  ];

  constructor(private translate: TranslateService) {
    this.currentLang.set(this.translate.currentLang || 'et');
  }

  setLanguage(lang: string) {
    this.translate.use(lang);
    this.currentLang.set(lang);
    localStorage.setItem('lang', lang);
  }
}
