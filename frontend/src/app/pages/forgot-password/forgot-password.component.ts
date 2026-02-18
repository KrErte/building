import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {
  email = '';
  loading = signal(false);
  error = signal<string | null>(null);
  success = signal(false);

  constructor(
    private authService: AuthService,
    private translate: TranslateService
  ) {}

  onSubmit(): void {
    if (!this.email) {
      this.translate.get('AUTH.ERROR_INVALID_EMAIL').subscribe(msg => this.error.set(msg));
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.authService.forgotPassword(this.email).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
      },
      error: () => {
        this.loading.set(false);
        this.success.set(true);
      }
    });
  }
}
