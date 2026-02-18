import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PipelineService } from '../../services/pipeline.service';
import { Pipeline, PIPELINE_STEP_LABELS } from '../../models/project.model';

@Component({
  selector: 'app-pipelines',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="pipelines-page">
      <header class="page-header">
        <div class="header-content">
          <h1>Töövood</h1>
          <p class="header-subtitle" *ngIf="pipelines().length > 0">{{ pipelines().length }} töövoogu</p>
        </div>
      </header>

      <div class="content">
        <!-- Loading -->
        <div class="loading" *ngIf="isLoading()">
          <div class="spinner"></div>
          <p>Laadin...</p>
        </div>

        <!-- Empty State -->
        <div class="empty-state" *ngIf="!isLoading() && pipelines().length === 0">
          <div class="empty-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
          </div>
          <h2>Töövoogusid pole veel</h2>
          <p>Töövood tekivad automaatselt, kui käivitad projekti töötluse.</p>
          <a routerLink="/projects" class="btn btn-primary">Vaata projekte</a>
        </div>

        <!-- Pipelines List -->
        <div class="pipelines-list" *ngIf="!isLoading() && pipelines().length > 0">
          <a [routerLink]="['/projects', p.projectId]" class="pipeline-card" *ngFor="let p of pipelines()">
            <div class="card-top">
              <span class="status-badge" [ngClass]="getStatusClass(p.status)">
                {{ getStatusLabel(p.status) }}
              </span>
              <span class="date">{{ formatDate(p.createdAt) }}</span>
            </div>

            <div class="card-progress">
              <div class="progress-info">
                <span class="progress-text">Samm {{ p.currentStep }}/{{ p.totalSteps }}</span>
                <span class="progress-percent">{{ p.progressPercent }}%</span>
              </div>
              <div class="progress-bar">
                <div class="progress-fill" [style.width.%]="p.progressPercent"
                     [class.running]="p.status === 'RUNNING'"
                     [class.completed]="p.status === 'COMPLETED'"
                     [class.failed]="p.status === 'FAILED'"></div>
              </div>
            </div>

            <div class="card-steps">
              <div class="step-dot" *ngFor="let step of p.steps"
                   [class.completed]="step.status === 'COMPLETED'"
                   [class.running]="step.status === 'RUNNING'"
                   [class.failed]="step.status === 'FAILED'"
                   [title]="getStepLabel(step.stepType)">
              </div>
            </div>

            <div class="card-error" *ngIf="p.errorMessage">
              {{ p.errorMessage }}
            </div>
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pipelines-page {
      min-height: 100vh;
      background: var(--bg-primary);
    }

    .page-header {
      padding: 24px;
      border-bottom: 1px solid var(--border-color);
      background: var(--bg-card);
    }

    .header-content {
      max-width: 1200px;
      margin: 0 auto;

      h1 {
        font-size: 24px;
        font-weight: 700;
        color: var(--text-primary);
      }
    }

    .header-subtitle {
      font-size: 14px;
      color: var(--text-secondary);
      margin-top: 4px;
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

      p { margin-top: 16px; color: var(--text-secondary); }
    }

    .spinner {
      width: 48px;
      height: 48px;
      border: 3px solid var(--border-color);
      border-top-color: var(--purple-primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin { to { transform: rotate(360deg); } }

    .empty-state {
      text-align: center;
      padding: 80px 20px;

      .empty-icon {
        margin-bottom: 24px;
        svg { width: 80px; height: 80px; color: var(--text-muted); }
      }

      h2 { font-size: 22px; color: var(--text-primary); margin-bottom: 8px; }
      p { color: var(--text-secondary); margin-bottom: 24px; }
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

      &.btn-primary {
        background: linear-gradient(135deg, var(--purple-primary), var(--purple-hover));
        color: white;
        &:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(139, 92, 246, 0.4); }
      }
    }

    .pipelines-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .pipeline-card {
      display: block;
      background: var(--bg-card);
      border: 1px solid var(--border-color);
      border-radius: 16px;
      padding: 20px;
      text-decoration: none;
      transition: all 0.2s;

      &:hover {
        border-color: rgba(139, 92, 246, 0.4);
        transform: translateY(-2px);
        box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
      }
    }

    .card-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .status-badge {
      padding: 4px 10px;
      border-radius: 16px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;

      &.status-running { background: rgba(34, 197, 94, 0.15); color: #22c55e; }
      &.status-completed { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }
      &.status-failed { background: rgba(239, 68, 68, 0.15); color: #ef4444; }
      &.status-paused { background: rgba(234, 179, 8, 0.15); color: #eab308; }
      &.status-created { background: rgba(107, 114, 128, 0.15); color: #9ca3af; }
    }

    .date { font-size: 12px; color: var(--text-muted); }

    .card-progress {
      margin-bottom: 16px;
    }

    .progress-info {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
    }

    .progress-text { font-size: 14px; color: var(--text-secondary); }
    .progress-percent { font-size: 14px; font-weight: 600; color: var(--purple-primary); }

    .progress-bar {
      height: 6px;
      background: var(--bg-secondary);
      border-radius: 3px;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.3s;
      background: var(--purple-primary);

      &.running { background: linear-gradient(90deg, var(--purple-primary), #22c55e); }
      &.completed { background: #22c55e; }
      &.failed { background: #ef4444; }
    }

    .card-steps {
      display: flex;
      gap: 6px;
    }

    .step-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--border-color);

      &.completed { background: #22c55e; }
      &.running { background: var(--purple-primary); animation: pulse 1.5s infinite; }
      &.failed { background: #ef4444; }
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.4; }
    }

    .card-error {
      margin-top: 12px;
      padding: 8px 12px;
      background: rgba(239, 68, 68, 0.1);
      border-radius: 8px;
      font-size: 13px;
      color: #ef4444;
    }
  `]
})
export class PipelinesComponent implements OnInit {
  pipelines = signal<Pipeline[]>([]);
  isLoading = signal(true);
  stepLabels = PIPELINE_STEP_LABELS;

  constructor(private pipelineService: PipelineService) {}

  ngOnInit(): void {
    this.pipelineService.listPipelines().subscribe({
      next: (pipelines) => {
        this.pipelines.set(pipelines);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'CREATED': return 'Loodud';
      case 'RUNNING': return 'Töötab';
      case 'PAUSED': return 'Peatatud';
      case 'COMPLETED': return 'Valmis';
      case 'FAILED': return 'Ebaõnnestus';
      case 'CANCELLED': return 'Tühistatud';
      default: return status;
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'RUNNING': return 'status-running';
      case 'COMPLETED': return 'status-completed';
      case 'FAILED': return 'status-failed';
      case 'PAUSED': return 'status-paused';
      default: return 'status-created';
    }
  }

  getStepLabel(stepType: string): string {
    return this.stepLabels[stepType] || stepType;
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    return date.toLocaleDateString('et-EE', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
