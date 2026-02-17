import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';
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

  user = this.currentUser.asReadonly();
  isLoggedIn = computed(() => !!this.currentUser());
  isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  constructor(private http: HttpClient, private router: Router) {
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    const storedUser = localStorage.getItem('bq_user');
    const storedToken = localStorage.getItem('bq_token');

    if (storedUser && storedToken) {
      try {
        this.currentUser.set(JSON.parse(storedUser));
        this.token.set(storedToken);
      } catch {
        this.clearStorage();
      }
    }
  }

  private saveToStorage(user: User, token: string): void {
    localStorage.setItem('bq_user', JSON.stringify(user));
    localStorage.setItem('bq_token', token);
  }

  private clearStorage(): void {
    localStorage.removeItem('bq_user');
    localStorage.removeItem('bq_token');
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/auth/login`, credentials).pipe(
      tap(response => {
        this.currentUser.set(response.user);
        this.token.set(response.token);
        this.saveToStorage(response.user, response.token);
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
        this.saveToStorage(response.user, response.token);
      }),
      catchError(error => {
        return throwError(() => error.error?.message || 'Registreerimine ebaõnnestus');
      })
    );
  }

  logout(): void {
    this.currentUser.set(null);
    this.token.set(null);
    this.clearStorage();
    this.router.navigate(['/']);
  }

  getToken(): string | null {
    return this.token();
  }

  getFullName(): string {
    const user = this.currentUser();
    return user ? `${user.firstName} ${user.lastName}` : '';
  }
}
