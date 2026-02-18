import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, throwError, BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';
import { User, LoginRequest, RegisterRequest, AuthResponse } from '../models/user.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = environment.apiUrl;

  private currentUser = signal<User | null>(null);
  private token = signal<string | null>(null);
  private refreshTokenValue = signal<string | null>(null);
  private refreshing$ = new BehaviorSubject<boolean>(false);

  user = this.currentUser.asReadonly();
  isLoggedIn = computed(() => !!this.currentUser());
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  constructor(private http: HttpClient, private router: Router) {
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    const storedUser = localStorage.getItem('bq_user');
    const storedToken = localStorage.getItem('bq_token');
    const storedRefresh = localStorage.getItem('bq_refresh_token');

    if (storedUser && storedToken) {
      try {
        this.currentUser.set(JSON.parse(storedUser));
        this.token.set(storedToken);
        this.refreshTokenValue.set(storedRefresh);
      } catch {
        this.clearStorage();
      }
    }
  }

  private saveToStorage(user: User, token: string, refreshToken?: string): void {
    localStorage.setItem('bq_user', JSON.stringify(user));
    localStorage.setItem('bq_token', token);
    if (refreshToken) {
      localStorage.setItem('bq_refresh_token', refreshToken);
    }
  }

  private clearStorage(): void {
    localStorage.removeItem('bq_user');
    localStorage.removeItem('bq_token');
    localStorage.removeItem('bq_refresh_token');
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap(response => {
        this.currentUser.set(response.user);
        this.token.set(response.token);
        this.refreshTokenValue.set(response.refreshToken || null);
        this.saveToStorage(response.user, response.token, response.refreshToken);
      }),
      catchError(error => {
        return throwError(() => error.error?.message || 'Sisselogimine ebaõnnestus');
      })
    );
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/register`, data).pipe(
      tap(response => {
        this.currentUser.set(response.user);
        this.token.set(response.token);
        this.refreshTokenValue.set(response.refreshToken || null);
        this.saveToStorage(response.user, response.token, response.refreshToken);
      }),
      catchError(error => {
        return throwError(() => error.error?.message || 'Registreerimine ebaõnnestus');
      })
    );
  }

  refreshToken(): Observable<AuthResponse> {
    const refresh = this.refreshTokenValue();
    if (!refresh) {
      return throwError(() => 'No refresh token');
    }
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/refresh`, { refreshToken: refresh }).pipe(
      tap(response => {
        this.token.set(response.token);
        this.refreshTokenValue.set(response.refreshToken || null);
        this.currentUser.set(response.user);
        this.saveToStorage(response.user, response.token, response.refreshToken);
      }),
      catchError(error => {
        this.forceLogout();
        return throwError(() => error.error?.message || 'Session expired');
      })
    );
  }

  logout(): void {
    const refresh = this.refreshTokenValue();
    if (refresh) {
      this.http.post(`${this.apiUrl}/auth/logout`, { refreshToken: refresh }).subscribe();
    }
    this.currentUser.set(null);
    this.token.set(null);
    this.refreshTokenValue.set(null);
    this.clearStorage();
    this.router.navigate(['/']);
  }

  private forceLogout(): void {
    this.currentUser.set(null);
    this.token.set(null);
    this.refreshTokenValue.set(null);
    this.clearStorage();
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return this.token();
  }

  getRefreshToken(): string | null {
    return this.refreshTokenValue();
  }

  isRefreshing(): boolean {
    return this.refreshing$.value;
  }

  getFullName(): string {
    const user = this.currentUser();
    return user ? `${user.firstName} ${user.lastName}` : '';
  }

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/auth/reset-password`, { token, newPassword });
  }

  verifyEmail(token: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/auth/verify-email`, { token });
  }

  getCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/auth/me`);
  }
}
