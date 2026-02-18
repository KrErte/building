import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../services/project.service';
import { RfqService } from '../../services/rfq.service';
import { Project, CATEGORY_LABELS, CATEGORY_ICONS } from '../../models/project.model';
import { Campaign } from '../../models/rfq.model';

@Component({
  selector: 'app-projects',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="projects-page">
      <header class="page-header">
        <div class="header-content">
          <div class="header-left">
            <h1>Projektid</h1>
            <p class="header-subtitle" *ngIf="projects().length > 0">{{ projects().length }} projekti</p>
          </div>
          <a routerLink="/projects/new" class="btn btn-primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Uus Projekt
          </a>
        </div>

        <!-- Tab switcher -->
        <div class="tabs">
          <button class="tab" [class.active]="activeTab() === 'projects'" (click)="activeTab.set('projects')">
            Projektid
            <span class="tab-count" *ngIf="projects().length > 0">{{ projects().length }}</span>
          </button>
          <button class="tab" [class.active]="activeTab() === 'campaigns'" (click)="switchToCampaigns()">
            Kampaaniad
            <span class="tab-count" *ngIf="campaigns().length > 0">{{ campaigns().length }}</span>
          </button>
        </div>
      </header>

      <div class="content">
        <!-- Loading -->
        <div class="loading" *ngIf="isLoading()">
          <div class="spinner"></div>
          <p>Laadin...</p>
        </div>

        <!-- Projects Tab -->
        <ng-container *ngIf="activeTab() === 'projects' && !isLoading()">
          <!-- Empty State -->
          <div class="empty-state" *ngIf="projects().length === 0">
            <div class="empty-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <h2>Projekte pole veel</h2>
            <p>Alusta uue projekti loomisega ja saada esimene hinnaparing.</p>
            <a routerLink="/projects/new" class="btn btn-primary">Loo esimene projekt</a>
          </div>

          <!-- Projects Grid -->
          <div class="projects-grid" *ngIf="projects().length > 0">
            <a [routerLink]="['/projects', project.id]" class="project-card" *ngFor="let project of projects()">
              <div class="card-header">
                <span class="status-badge" [ngClass]="getProjectStatusClass(project.status)">
                  {{ getProjectStatusLabel(project.status) }}
                </span>
                <span class="date">{{ formatDate(project.createdAt) }}</span>
              </div>

              <h3>{{ project.title }}</h3>

              <div class="card-meta">
                <span class="meta-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                  {{ project.location || 'Maaramata' }}
                </span>
                <span class="meta-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                  {{ project.stages.length }} etappi
                </span>
              </div>

              <div class="card-stats">
                <div class="stat">
                  <span class="stat-value">{{ formatCurrency(project.totalEstimateMin) }}</span>
                  <span class="stat-label">Min hind</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ formatCurrency(project.totalEstimateMax) }}</span>
                  <span class="stat-label">Max hind</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ project.totalSupplierCount }}</span>
                  <span class="stat-label">Tegijad</span>
                </div>
              </div>

              <div class="card-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </div>
            </a>
          </div>
        </ng-container>

        <!-- Campaigns Tab -->
        <ng-container *ngIf="activeTab() === 'campaigns' && !isLoading()">
          <div class="empty-state" *ngIf="campaigns().length === 0">
            <div class="empty-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <line x1="22" y1="2" x2="11" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </div>
            <h2>Kampaaniaid pole veel</h2>
            <p>Kampaaniad tekivad, kui saadad hinnapäringuid.</p>
          </div>

          <div class="projects-grid" *ngIf="campaigns().length > 0">
            <a [routerLink]="['/projects', campaign.id]" [queryParams]="{type: 'campaign'}" class="project-card" *ngFor="let campaign of campaigns()">
              <div class="card-header">
                <span class="status-badge" [ngClass]="getCampaignStatusClass(campaign.status)">
                  {{ getCampaignStatusLabel(campaign.status) }}
                </span>
                <span class="date">{{ formatDate(campaign.createdAt) }}</span>
              </div>

              <h3>{{ campaign.title }}</h3>

              <div class="card-meta">
                <span class="meta-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                    <circle cx="12" cy="10" r="3"/>
                  </svg>
                  {{ campaign.location || 'Maaramata' }}
                </span>
                <span class="meta-item">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
                    <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
                  </svg>
                  {{ campaign.category }}
                </span>
              </div>

              <div class="card-stats">
                <div class="stat">
                  <span class="stat-value">{{ campaign.totalSent }}</span>
                  <span class="stat-label">Saadetud</span>
                </div>
                <div class="stat">
                  <span class="stat-value">{{ campaign.totalResponded }}</span>
                  <span class="stat-label">Vastanud</span>
                </div>
                <div class="stat" *ngIf="campaign.maxBudget">
                  <span class="stat-value">{{ formatCurrency(campaign.maxBudget) }}</span>
                  <span class="stat-label">Eelarve</span>
                </div>
              </div>

              <div class="card-arrow">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </div>
            </a>
          </div>
        </ng-container>
      </div>
    </div>
  `,
  styles: [`
    .projects-page {
      min-height: 100vh;
      background: var(--bg-primary);
    }

    .page-header {
      padding: 24px 24px 0;
      border-bottom: 1px solid var(--border-color);
      background: var(--bg-card);
    }

    .header-content {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .header-left {
      h1 {
        font-size: 24px;
        font-weight: 700;
        color: var(--text-primary);
      }
    }

    .header-subtitle {
      font-size: 14px;
      color: var(--text-secondary);
      margin-top: 2px;
    }

    .tabs {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      gap: 4px;
    }

    .tab {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 20px;
      background: none;
      color: var(--text-secondary);
      font-size: 14px;
      font-weight: 500;
      border-bottom: 2px solid transparent;
      transition: all 0.2s;

      &:hover {
        color: var(--text-primary);
      }

      &.active {
        color: var(--purple-primary);
        border-bottom-color: var(--purple-primary);
      }
    }

    .tab-count {
      background: var(--bg-secondary);
      padding: 2px 8px;
      border-radius: 10px;
      font-size: 12px;
      font-weight: 600;

      .active & {
        background: rgba(139, 92, 246, 0.15);
        color: var(--purple-primary);
      }
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 12px 20px;
      border-radius: 10px;
      font-weight: 600;
      font-size: 14px;
      text-decoration: none;
      border: none;
      cursor: pointer;
      transition: all 0.2s;

      svg {
        width: 18px;
        height: 18px;
      }

      &.btn-primary {
        background: linear-gradient(135deg, var(--purple-primary), var(--purple-hover));
        color: white;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 8px 20px rgba(139, 92, 246, 0.4);
        }
      }
    }

    .content {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px;
    }

    .loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 400px;

      p {
        margin-top: 16px;
        color: var(--text-secondary);
      }
    }

    .spinner {
      width: 48px;
      height: 48px;
      border: 3px solid var(--border-color);
      border-top-color: var(--purple-primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state {
      text-align: center;
      padding: 80px 20px;

      .empty-icon {
        margin-bottom: 24px;

        svg {
          width: 80px;
          height: 80px;
          color: var(--text-muted);
        }
      }

      h2 {
        font-size: 22px;
        color: var(--text-primary);
        margin-bottom: 8px;
      }

      p {
        color: var(--text-secondary);
        margin-bottom: 24px;
        max-width: 400px;
        margin-left: auto;
        margin-right: auto;
      }
    }

    .projects-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 20px;
    }

    .project-card {
      position: relative;
      display: block;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 16px;
      padding: 20px;
      text-decoration: none;
      transition: all 0.2s;

      &:hover {
        border-color: rgba(139, 92, 246, 0.4);
        transform: translateY(-4px);
        box-shadow: 0 12px 32px rgba(0, 0, 0, 0.2);

        .card-arrow {
          transform: translateX(4px);
          opacity: 1;
        }
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .status-badge {
      padding: 4px 10px;
      border-radius: 16px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;

      &.status-draft {
        background: rgba(107, 114, 128, 0.15);
        color: #9ca3af;
      }

      &.status-parsed {
        background: rgba(59, 130, 246, 0.15);
        color: #3b82f6;
      }

      &.status-quoting {
        background: rgba(234, 179, 8, 0.15);
        color: #eab308;
      }

      &.status-active {
        background: rgba(34, 197, 94, 0.15);
        color: #22c55e;
      }

      &.status-sending {
        background: rgba(59, 130, 246, 0.15);
        color: #3b82f6;
      }

      &.status-completed {
        background: rgba(139, 92, 246, 0.15);
        color: #8b5cf6;
      }

      &.status-archived {
        background: rgba(107, 114, 128, 0.15);
        color: #6b7280;
      }
    }

    .date {
      font-size: 12px;
      color: var(--text-muted);
    }

    h3 {
      font-size: 18px;
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: 16px;
      line-height: 1.4;
    }

    .card-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 16px;
    }

    .meta-item {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: var(--text-secondary);

      svg {
        width: 14px;
        height: 14px;
      }
    }

    .card-stats {
      display: flex;
      gap: 20px;
      padding-top: 16px;
      border-top: 1px solid var(--border-color);
    }

    .stat {
      display: flex;
      flex-direction: column;

      .stat-value {
        font-size: 18px;
        font-weight: 700;
        color: var(--text-primary);
      }

      .stat-label {
        font-size: 11px;
        color: var(--text-muted);
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
    }

    .card-arrow {
      position: absolute;
      right: 20px;
      top: 50%;
      transform: translateY(-50%);
      opacity: 0;
      transition: all 0.2s;

      svg {
        width: 20px;
        height: 20px;
        color: var(--purple-primary);
      }
    }

    @media (max-width: 640px) {
      .header-content {
        flex-direction: column;
        gap: 16px;
        text-align: center;
      }

      .projects-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class ProjectsComponent implements OnInit {
  projects = signal<Project[]>([]);
  campaigns = signal<Campaign[]>([]);
  isLoading = signal(true);
  activeTab = signal<'projects' | 'campaigns'>('projects');

  constructor(
    private projectService: ProjectService,
    private rfqService: RfqService
  ) {}

  ngOnInit(): void {
    this.loadProjects();
  }

  loadProjects(): void {
    this.isLoading.set(true);
    this.projectService.listProjects().subscribe({
      next: (projects) => {
        this.projects.set(projects);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Error loading projects:', err);
        this.isLoading.set(false);
      }
    });
  }

  switchToCampaigns(): void {
    this.activeTab.set('campaigns');
    if (this.campaigns().length === 0) {
      this.isLoading.set(true);
      this.rfqService.getCampaigns().subscribe({
        next: (campaigns) => {
          this.campaigns.set(campaigns);
          this.isLoading.set(false);
        },
        error: () => this.isLoading.set(false)
      });
    }
  }

  getProjectStatusLabel(status: string): string {
    switch (status) {
      case 'DRAFT': return 'Mustand';
      case 'PARSED': return 'Analüüsitud';
      case 'QUOTING': return 'Päringud';
      case 'COMPLETED': return 'Lõppenud';
      case 'ARCHIVED': return 'Arhiveeritud';
      default: return status;
    }
  }

  getProjectStatusClass(status: string): string {
    switch (status) {
      case 'DRAFT': return 'status-draft';
      case 'PARSED': return 'status-parsed';
      case 'QUOTING': return 'status-quoting';
      case 'COMPLETED': return 'status-completed';
      case 'ARCHIVED': return 'status-archived';
      default: return '';
    }
  }

  getCampaignStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'Aktiivne';
      case 'SENDING': return 'Saadan...';
      case 'COMPLETED': return 'Lõppenud';
      default: return status;
    }
  }

  getCampaignStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'status-active';
      case 'SENDING': return 'status-sending';
      case 'COMPLETED': return 'status-completed';
      default: return '';
    }
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    return date.toLocaleDateString('et-EE', {
      day: 'numeric',
      month: 'short'
    });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('et-EE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  }
}
