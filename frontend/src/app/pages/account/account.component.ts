import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50">
      <div class="max-w-2xl mx-auto px-4 py-8">
        <a routerLink="/projects" class="text-violet-600 hover:text-violet-700 flex items-center gap-2 mb-6">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
          Tagasi
        </a>

        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="bg-gradient-to-r from-violet-500 to-indigo-600 px-6 py-8 text-white">
            <div class="w-16 h-16 bg-white/20 rounded-full flex items-center justify-center text-2xl font-bold mb-4">
              {{ getInitials() }}
            </div>
            <h1 class="text-2xl font-bold">{{ user()?.firstName }} {{ user()?.lastName }}</h1>
            <p class="opacity-90">{{ user()?.email }}</p>
          </div>

          <div class="p-6 space-y-4">
            <div class="flex justify-between items-center py-3 border-b border-gray-100">
              <span class="text-gray-600">Eesnimi</span>
              <span class="font-medium">{{ user()?.firstName || '-' }}</span>
            </div>
            <div class="flex justify-between items-center py-3 border-b border-gray-100">
              <span class="text-gray-600">Perekonnanimi</span>
              <span class="font-medium">{{ user()?.lastName || '-' }}</span>
            </div>
            <div class="flex justify-between items-center py-3 border-b border-gray-100">
              <span class="text-gray-600">E-post</span>
              <span class="font-medium">{{ user()?.email || '-' }}</span>
            </div>
            <div class="flex justify-between items-center py-3 border-b border-gray-100">
              <span class="text-gray-600">Ettevõte</span>
              <span class="font-medium">{{ user()?.company || '-' }}</span>
            </div>

            <div class="pt-4 flex gap-3">
              <a routerLink="/settings" class="flex-1 py-3 bg-gray-100 text-gray-700 font-medium rounded-lg hover:bg-gray-200 transition text-center">
                Seaded
              </a>
              <button
                (click)="logout()"
                class="flex-1 py-3 bg-red-50 text-red-600 font-medium rounded-lg hover:bg-red-100 transition"
              >
                Logi välja
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AccountComponent implements OnInit {
  user = signal<any>(null);

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit() {
    const currentUser = this.authService.user();
    if (!currentUser) {
      this.router.navigate(['/login']);
      return;
    }
    this.user.set(currentUser);
  }

  getInitials(): string {
    const u = this.user();
    if (!u) return '?';
    const first = u.firstName?.charAt(0) || '';
    const last = u.lastName?.charAt(0) || '';
    return (first + last).toUpperCase() || '?';
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
