import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/landing/landing.component')
      .then(m => m.LandingComponent)
  },
  {
    path: 'projects',
    loadComponent: () => import('./pages/projects/projects.component')
      .then(m => m.ProjectsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'projects/new',
    loadComponent: () => import('./pages/project-new/project-new.component')
      .then(m => m.ProjectNewComponent),
    canActivate: [authGuard]
  },
  {
    path: 'projects/:id',
    loadComponent: () => import('./pages/project-detail/project-detail.component')
      .then(m => m.ProjectDetailComponent),
    canActivate: [authGuard]
  },
  {
    path: 'pipelines',
    loadComponent: () => import('./pages/pipelines/pipelines.component')
      .then(m => m.PipelinesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'wizard',
    loadComponent: () => import('./pages/wizard/wizard.component')
      .then(m => m.WizardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'companies',
    loadComponent: () => import('./pages/companies/companies.component')
      .then(m => m.CompaniesComponent)
  },
  {
    path: 'bid/:token',
    loadComponent: () => import('./pages/bid/bid.component')
      .then(m => m.BidComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component')
      .then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register.component')
      .then(m => m.RegisterComponent)
  },
  {
    path: 'onboard/:token',
    loadComponent: () => import('./pages/onboard/onboard.component')
      .then(m => m.OnboardComponent)
  },
  {
    path: 'admin',
    loadComponent: () => import('./pages/admin/admin.component')
      .then(m => m.AdminComponent),
    canActivate: [authGuard]
  },
  {
    path: 'account',
    loadComponent: () => import('./pages/account/account.component')
      .then(m => m.AccountComponent),
    canActivate: [authGuard]
  },
  {
    path: 'settings',
    loadComponent: () => import('./pages/settings/settings.component')
      .then(m => m.SettingsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'terms',
    loadComponent: () => import('./pages/terms/terms.component')
      .then(m => m.TermsComponent)
  },
  {
    path: 'privacy',
    loadComponent: () => import('./pages/privacy/privacy.component')
      .then(m => m.PrivacyComponent)
  },
  {
    path: 'about',
    loadComponent: () => import('./pages/about/about.component')
      .then(m => m.AboutComponent)
  }
];
