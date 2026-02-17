import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  name = '';
  email = '';
  company = '';
  password = '';
  confirmPassword = '';
  acceptTerms = false;

  error = signal<string | null>(null);
  loading = signal(false);
  showPassword = signal(false);

  constructor(
    private authService: AuthService,
    private router: Router,
    private translate: TranslateService
  ) {}

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  onSubmit(): void {
    // Validation
    if (!this.name || !this.email || !this.password || !this.confirmPassword) {
      this.translate.get('AUTH.ERROR_FILL_ALL').subscribe(msg => this.error.set(msg));
      return;
    }

    if (this.password.length < 8) {
      this.translate.get('AUTH.ERROR_PASSWORD_LENGTH').subscribe(msg => this.error.set(msg));
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.translate.get('AUTH.ERROR_PASSWORDS_MATCH').subscribe(msg => this.error.set(msg));
      return;
    }

    if (!this.acceptTerms) {
      this.translate.get('AUTH.ERROR_FILL_ALL').subscribe(msg => this.error.set(msg));
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    // Split name into firstName and lastName
    const nameParts = this.name.trim().split(' ');
    const firstName = nameParts[0] || '';
    const lastName = nameParts.slice(1).join(' ') || '';

    this.authService.register({
      firstName,
      lastName,
      email: this.email,
      password: this.password,
      company: this.company || undefined
    }).subscribe({
      next: () => {
        this.router.navigate(['/projects']);
      },
      error: (err) => {
        this.error.set(err);
        this.loading.set(false);
      }
    });
  }
}
