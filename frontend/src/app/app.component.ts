import { Component, computed, signal, HostListener } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs/operators';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  template: `
    @if (showHeader()) {
      <header class="navbar">
        <div class="navbar-inner">
          <!-- LEFT: Logo -->
          <a routerLink="/" class="logo">
            <span class="logo-bolt">⚡</span>
            <span class="logo-text">BuildQuote</span>
          </a>

          <!-- CENTER: Navigation Links -->
          <nav class="nav-center">
            <a routerLink="/projects/new" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              <span>Uus Projekt</span>
            </a>
            <a routerLink="/projects" routerLinkActive="active" [routerLinkActiveOptions]="{exact: true}" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
              <span>Projektid</span>
            </a>
            <a routerLink="/companies" routerLinkActive="active" class="nav-item">
              <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 21h18"/>
                <path d="M5 21V7l8-4v18"/>
                <path d="M19 21V11l-6-4"/>
                <path d="M9 9v.01"/><path d="M9 12v.01"/><path d="M9 15v.01"/><path d="M9 18v.01"/>
              </svg>
              <span>Ettevotted</span>
            </a>
          </nav>

          <!-- RIGHT: Search, Notifications, User -->
          <div class="nav-right">
            <!-- Search Bar -->
            <div class="search-wrapper" [class.expanded]="searchFocused()">
              <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input
                type="text"
                class="search-input"
                placeholder="Otsi ettevõtteid..."
                [(ngModel)]="searchQuery"
                (focus)="searchFocused.set(true)"
                (blur)="onSearchBlur()"
                (keyup.enter)="performSearch()"
              />
              @if (searchFocused() && searchQuery) {
                <button class="search-clear" (click)="clearSearch()">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              }
            </div>

            <!-- Notifications hidden until feature is implemented -->
            <!--
            <button class="icon-btn" (click)="toggleNotifications()">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              @if (hasNotifications()) {
                <span class="notification-dot"></span>
              }
            </button>
            -->

            <!-- User Menu -->
            <div class="user-menu-wrapper">
              <button class="user-avatar" (click)="toggleUserMenu()">
                <span class="avatar-letter">{{ userInitial() }}</span>
              </button>

              @if (userMenuOpen()) {
                <div class="user-dropdown" (click)="$event.stopPropagation()">
                  <div class="dropdown-header">
                    <div class="user-avatar-large">
                      <span class="avatar-letter">{{ userInitial() }}</span>
                    </div>
                    <div class="user-info">
                      <span class="user-name">{{ authService.getFullName() || 'Kasutaja' }}</span>
                      <span class="user-email">{{ authService.user()?.email || '' }}</span>
                    </div>
                  </div>
                  <div class="dropdown-divider"></div>
                  <a routerLink="/account" class="dropdown-item" (click)="closeMenus()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                    <span>Minu konto</span>
                  </a>
                  <a routerLink="/settings" class="dropdown-item" (click)="closeMenus()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="3"/>
                      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                    </svg>
                    <span>Seaded</span>
                  </a>
                  <!-- Language selector hidden until i18n is fully implemented -->
                  <!--
                  <div class="dropdown-item lang-selector" (click)="$event.stopPropagation()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/>
                      <line x1="2" y1="12" x2="22" y2="12"/>
                      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
                    </svg>
                    <span>Keel</span>
                    <div class="lang-toggle">
                      @for (lang of languages; track lang) {
                        <button
                          type="button"
                          class="lang-btn"
                          [class.active]="currentLang() === lang"
                          (click)="selectLanguage($event, lang)">
                          {{ lang }}
                        </button>
                      }
                    </div>
                  </div>
                  -->
                  <div class="dropdown-divider"></div>
                  <button class="dropdown-item logout" (click)="logout()">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                      <polyline points="16 17 21 12 16 7"/>
                      <line x1="21" y1="12" x2="9" y2="12"/>
                    </svg>
                    <span>Logi välja</span>
                  </button>
                </div>
              }
            </div>
          </div>
        </div>
        <div class="navbar-gradient-line"></div>
      </header>
    }
    <main [class.with-header]="showHeader()">
      <router-outlet></router-outlet>
    </main>
  `,
  styles: [`
    /* ============ NAVBAR BASE ============ */
    .navbar {
      position: sticky;
      top: 0;
      z-index: 1000;
      background: rgba(18, 18, 26, 0.85);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    }

    .navbar-inner {
      max-width: 1400px;
      margin: 0 auto;
      padding: 12px 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 32px;
    }

    .navbar-gradient-line {
      height: 2px;
      background: linear-gradient(90deg, transparent, #8b5cf6, #3b82f6, #8b5cf6, transparent);
      opacity: 0.6;
    }

    /* ============ LOGO ============ */
    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      text-decoration: none;
      flex-shrink: 0;

      &:hover .logo-bolt {
        animation: bolt-pulse 0.6s ease-in-out;
        filter: drop-shadow(0 0 8px #8b5cf6);
      }
    }

    .logo-bolt {
      font-size: 28px;
      transition: all 0.3s ease;
    }

    .logo-text {
      font-size: 22px;
      font-weight: 800;
      background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 50%, #3b82f6 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    @keyframes bolt-pulse {
      0%, 100% { transform: scale(1); }
      25% { transform: scale(1.3) rotate(-10deg); }
      50% { transform: scale(0.9); }
      75% { transform: scale(1.15) rotate(5deg); }
    }

    /* ============ CENTER NAV ============ */
    .nav-center {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 16px;
      border-radius: 10px;
      color: #a1a1aa;
      text-decoration: none;
      font-weight: 500;
      font-size: 14px;
      transition: all 0.2s ease;
      position: relative;

      &:hover {
        color: #fff;
        background: rgba(139, 92, 246, 0.1);
      }

      &.active {
        color: #fff;
        background: rgba(139, 92, 246, 0.15);

        .nav-icon {
          color: #8b5cf6;
        }
      }
    }

    .nav-icon {
      width: 18px;
      height: 18px;
      transition: color 0.2s ease;
    }

    .badge {
      position: absolute;
      top: 4px;
      right: 4px;
      min-width: 18px;
      height: 18px;
      padding: 0 5px;
      background: linear-gradient(135deg, #ef4444, #dc2626);
      color: white;
      font-size: 11px;
      font-weight: 700;
      border-radius: 9px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px rgba(239, 68, 68, 0.4);
      animation: badge-pop 0.3s ease;
    }

    @keyframes badge-pop {
      0% { transform: scale(0); }
      50% { transform: scale(1.2); }
      100% { transform: scale(1); }
    }

    /* ============ RIGHT SECTION ============ */
    .nav-right {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    /* ============ SEARCH ============ */
    .search-wrapper {
      position: relative;
      display: flex;
      align-items: center;
      width: 180px;
      height: 40px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      overflow: hidden;

      &:hover {
        background: rgba(255, 255, 255, 0.08);
        border-color: rgba(139, 92, 246, 0.3);
      }

      &.expanded {
        width: 280px;
        background: rgba(139, 92, 246, 0.08);
        border-color: rgba(139, 92, 246, 0.4);
        box-shadow: 0 0 20px rgba(139, 92, 246, 0.15);
      }
    }

    .search-icon {
      position: absolute;
      left: 12px;
      width: 18px;
      height: 18px;
      color: #71717a;
      pointer-events: none;
      transition: color 0.2s ease;

      .expanded & {
        color: #8b5cf6;
      }
    }

    .search-input {
      width: 100%;
      height: 100%;
      padding: 0 36px 0 40px;
      background: transparent;
      border: none;
      color: #fff;
      font-size: 14px;
      outline: none;

      &::placeholder {
        color: #71717a;
      }
    }

    .search-clear {
      position: absolute;
      right: 8px;
      width: 24px;
      height: 24px;
      background: rgba(255, 255, 255, 0.1);
      border: none;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s ease;

      svg {
        width: 14px;
        height: 14px;
        color: #a1a1aa;
      }

      &:hover {
        background: rgba(239, 68, 68, 0.2);

        svg {
          color: #ef4444;
        }
      }
    }

    /* ============ ICON BUTTONS ============ */
    .icon-btn {
      position: relative;
      width: 40px;
      height: 40px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s ease;

      svg {
        width: 20px;
        height: 20px;
        color: #a1a1aa;
        transition: color 0.2s ease;
      }

      &:hover {
        background: rgba(139, 92, 246, 0.1);
        border-color: rgba(139, 92, 246, 0.3);

        svg {
          color: #8b5cf6;
        }
      }
    }

    .notification-dot {
      position: absolute;
      top: 8px;
      right: 8px;
      width: 8px;
      height: 8px;
      background: #ef4444;
      border-radius: 50%;
      box-shadow: 0 0 8px rgba(239, 68, 68, 0.6);
      animation: pulse-dot 2s ease-in-out infinite;
    }

    @keyframes pulse-dot {
      0%, 100% { transform: scale(1); opacity: 1; }
      50% { transform: scale(1.2); opacity: 0.8; }
    }

    /* ============ USER MENU ============ */
    .user-menu-wrapper {
      position: relative;
    }

    .user-avatar {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: linear-gradient(135deg, #8b5cf6, #6366f1);
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
      box-shadow: 0 2px 10px rgba(139, 92, 246, 0.3);

      &:hover {
        transform: scale(1.05);
        box-shadow: 0 4px 15px rgba(139, 92, 246, 0.4);
      }
    }

    .avatar-letter {
      color: white;
      font-size: 16px;
      font-weight: 700;
      text-transform: uppercase;
    }

    .user-dropdown {
      position: absolute;
      top: calc(100% + 12px);
      right: 0;
      width: 280px;
      background: rgba(26, 26, 36, 0.95);
      backdrop-filter: blur(20px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 16px;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
      overflow: hidden;
      animation: dropdown-appear 0.2s ease;
      z-index: 100;
    }

    @keyframes dropdown-appear {
      from {
        opacity: 0;
        transform: translateY(-10px) scale(0.95);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }

    .dropdown-header {
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 14px;
      background: linear-gradient(135deg, rgba(139, 92, 246, 0.15), rgba(99, 102, 241, 0.1));
    }

    .user-avatar-large {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: linear-gradient(135deg, #8b5cf6, #6366f1);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .avatar-letter {
        font-size: 20px;
      }
    }

    .user-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .user-name {
      font-weight: 600;
      color: #fff;
      font-size: 15px;
    }

    .user-email {
      font-size: 13px;
      color: #a1a1aa;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .dropdown-divider {
      height: 1px;
      background: rgba(255, 255, 255, 0.08);
      margin: 4px 0;
    }

    .dropdown-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 20px;
      color: #a1a1aa;
      text-decoration: none;
      font-size: 14px;
      transition: all 0.15s ease;
      cursor: pointer;
      border: none;
      background: none;
      width: 100%;
      text-align: left;

      svg {
        width: 18px;
        height: 18px;
        flex-shrink: 0;
      }

      &:hover {
        background: rgba(139, 92, 246, 0.1);
        color: #fff;
      }

      &.logout {
        color: #ef4444;

        &:hover {
          background: rgba(239, 68, 68, 0.1);
        }
      }
    }

    .lang-selector {
      justify-content: flex-start;

      span {
        flex: 1;
      }
    }

    .lang-toggle {
      display: flex;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 8px;
      padding: 2px;
    }

    .lang-btn {
      padding: 6px 10px;
      background: transparent;
      border: none;
      color: #71717a;
      font-size: 12px;
      font-weight: 600;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.15s ease;

      &:hover {
        color: #a1a1aa;
      }

      &.active {
        background: linear-gradient(135deg, #8b5cf6, #6366f1);
        color: white;
      }
    }

    /* ============ MAIN CONTENT ============ */
    main {
      min-height: 100vh;

      &.with-header {
        min-height: calc(100vh - 60px);
      }
    }

    /* ============ RESPONSIVE ============ */
    @media (max-width: 1024px) {
      .nav-center {
        display: none;
      }

      .search-wrapper {
        width: 44px;

        &.expanded {
          position: absolute;
          left: 80px;
          right: 160px;
          width: auto;
        }
      }

      .search-input {
        opacity: 0;
        .expanded & { opacity: 1; }
      }
    }

    @media (max-width: 640px) {
      .navbar-inner {
        padding: 10px 16px;
      }

      .search-wrapper {
        display: none;
      }
    }
  `]
})
export class AppComponent {
  title = 'BuildQuote';

  // UI State
  searchFocused = signal(false);
  searchQuery = '';
  userMenuOpen = signal(false);
  notificationsOpen = signal(false);
  pendingBids = signal(3); // Mock data
  hasNotifications = signal(true); // Mock data
  currentLang = signal('ET');
  languages = ['ET', 'EN', 'RU'];

  private currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(event => event.urlAfterRedirects)
    ),
    { initialValue: this.router.url }
  );

  // Pages that have their own navbar (or no navbar at all)
  private pagesWithOwnNavbar = ['/', '/login', '/register', '/wizard'];

  // Pages that should show no navbar (check with startsWith)
  private pagesWithNoNavbar = ['/bid/'];

  showHeader = computed(() => {
    const url = this.currentUrl();
    if (this.pagesWithOwnNavbar.includes(url)) return false;
    if (this.pagesWithNoNavbar.some(prefix => url.startsWith(prefix))) return false;
    return true;
  });

  userInitial = computed(() => {
    const user = this.authService.user();
    if (user?.firstName) {
      return user.firstName.charAt(0).toUpperCase();
    }
    return 'K';
  });

  constructor(
    private router: Router,
    public authService: AuthService
  ) {
    const savedLang = localStorage.getItem('buildquote_lang');
    if (savedLang && this.languages.includes(savedLang)) {
      this.currentLang.set(savedLang);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    this.closeMenus();
  }

  onSearchBlur(): void {
    setTimeout(() => {
      if (!this.searchQuery) {
        this.searchFocused.set(false);
      }
    }, 150);
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.searchFocused.set(false);
  }

  performSearch(): void {
    if (this.searchQuery.trim()) {
      this.router.navigate(['/companies'], { queryParams: { search: this.searchQuery } });
      this.searchFocused.set(false);
    }
  }

  toggleNotifications(): void {
    this.notificationsOpen.update(v => !v);
    this.userMenuOpen.set(false);
  }

  toggleUserMenu(): void {
    event?.stopPropagation();
    this.userMenuOpen.update(v => !v);
    this.notificationsOpen.set(false);
  }

  closeMenus(): void {
    this.userMenuOpen.set(false);
    this.notificationsOpen.set(false);
  }

  selectLanguage(event: Event, lang: string): void {
    event.stopPropagation();
    this.currentLang.set(lang);
    localStorage.setItem('buildquote_lang', lang);
  }

  logout(): void {
    this.authService.logout();
    this.closeMenus();
  }
}
