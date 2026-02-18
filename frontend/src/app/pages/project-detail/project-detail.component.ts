import { Component, signal, computed, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProjectService } from '../../services/project.service';
import { RfqService } from '../../services/rfq.service';
import { PipelineService } from '../../services/pipeline.service';
import { AnalysisService } from '../../services/analysis.service';
import {
  Project, Pipeline, PipelineStep, ScoredSupplier,
  ComparisonResult, NegotiationStrategy,
  CATEGORY_LABELS, CATEGORY_ICONS, PIPELINE_STEP_LABELS
} from '../../models/project.model';
import { Campaign, Bid } from '../../models/rfq.model';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './project-detail.component.html',
  styleUrls: ['./project-detail.component.scss']
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
  // Project mode signals
  project = signal<Project | null>(null);
  pipeline = signal<Pipeline | null>(null);
  comparison = signal<ComparisonResult | null>(null);
  negotiation = signal<NegotiationStrategy | null>(null);

  // Legacy campaign mode signals
  campaignId = signal<string>('');
  campaign = signal<Campaign | null>(null);
  bids = signal<Bid[]>([]);

  // UI state
  isLoading = signal(true);
  isLoadingBids = signal(false);
  isLoadingPipeline = signal(false);
  isStartingPipeline = signal(false);
  isLoadingComparison = signal(false);
  isLoadingNegotiation = signal(false);
  error = signal<string | null>(null);
  activeSection = signal<'stages' | 'pipeline' | 'bids' | 'analysis'>('stages');
  isCampaignMode = signal(false);

  private pollInterval: any = null;

  categoryLabels = CATEGORY_LABELS;
  categoryIcons = CATEGORY_ICONS;
  stepLabels = PIPELINE_STEP_LABELS;

  sortedBids = computed(() => {
    const bidList = this.bids();
    return [...bidList].sort((a, b) => a.price - b.price);
  });

  bestBid = computed(() => {
    const sorted = this.sortedBids();
    return sorted.length > 0 ? sorted[0] : null;
  });

  averagePrice = computed(() => {
    const bidList = this.bids();
    if (bidList.length === 0) return 0;
    const total = bidList.reduce((sum, b) => sum + b.price, 0);
    return total / bidList.length;
  });

  potentialSavings = computed(() => {
    const bidList = this.bids();
    if (bidList.length < 2) return 0;
    const prices = bidList.map(b => b.price).sort((a, b) => a - b);
    return prices[prices.length - 1] - prices[0];
  });

  constructor(
    private route: ActivatedRoute,
    private projectService: ProjectService,
    private rfqService: RfqService,
    private pipelineService: PipelineService,
    private analysisService: AnalysisService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    const type = this.route.snapshot.queryParamMap.get('type');

    if (!id) {
      this.error.set('Projekti ID puudub');
      this.isLoading.set(false);
      return;
    }

    if (type === 'campaign') {
      this.isCampaignMode.set(true);
      this.campaignId.set(id);
      this.loadCampaign(id);
    } else {
      this.loadProject(id);
    }
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  // Project loading
  loadProject(id: string): void {
    this.projectService.getProject(id).subscribe({
      next: (project) => {
        this.project.set(project);
        this.isLoading.set(false);
        this.loadPipeline(id);
      },
      error: (err) => {
        // Fallback to campaign mode
        this.isCampaignMode.set(true);
        this.campaignId.set(id);
        this.loadCampaign(id);
      }
    });
  }

  // Pipeline
  loadPipeline(projectId: string): void {
    this.isLoadingPipeline.set(true);
    this.pipelineService.getProjectPipelines(projectId).subscribe({
      next: (pipelines) => {
        if (pipelines.length > 0) {
          const match = pipelines[0]; // Most recent pipeline
          this.pipeline.set(match);
          // Auto-switch to pipeline tab when pipeline exists
          this.activeSection.set('pipeline');
          if (match.status === 'RUNNING') {
            this.startPolling(match.id);
          }
        }
        this.isLoadingPipeline.set(false);
      },
      error: () => {
        // Fallback to list-all approach
        this.pipelineService.listPipelines().subscribe({
          next: (pipelines) => {
            const match = pipelines.find(p => p.projectId === projectId);
            if (match) {
              this.pipeline.set(match);
              this.activeSection.set('pipeline');
              if (match.status === 'RUNNING') {
                this.startPolling(match.id);
              }
            }
            this.isLoadingPipeline.set(false);
          },
          error: () => this.isLoadingPipeline.set(false)
        });
      }
    });
  }

  resumePipeline(): void {
    const p = this.pipeline();
    if (!p) return;

    this.pipelineService.resumePipeline(p.id).subscribe({
      next: () => {
        this.startPolling(p.id);
      },
      error: (err) => console.error('Failed to resume pipeline:', err)
    });
  }

  startPipeline(): void {
    const proj = this.project();
    if (!proj) return;

    this.isStartingPipeline.set(true);
    this.pipelineService.createPipeline(proj.id).subscribe({
      next: (pipeline) => {
        this.pipeline.set(pipeline);
        this.isStartingPipeline.set(false);
        this.activeSection.set('pipeline');
        this.startPolling(pipeline.id);
      },
      error: (err) => {
        console.error('Failed to start pipeline:', err);
        this.isStartingPipeline.set(false);
      }
    });
  }

  private startPolling(pipelineId: string): void {
    this.stopPolling();
    this.pollInterval = setInterval(() => {
      this.pipelineService.getPipeline(pipelineId).subscribe({
        next: (pipeline) => {
          this.pipeline.set(pipeline);
          if (pipeline.status !== 'RUNNING') {
            this.stopPolling();
          }
        }
      });
    }, 3000);
  }

  private stopPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  // Campaign mode
  loadCampaign(id: string): void {
    this.rfqService.getCampaign(id).subscribe({
      next: (campaign) => {
        this.campaign.set(campaign);
        this.isLoading.set(false);
        this.activeSection.set('bids');
        this.loadBidsWithAnalysis(id);
      },
      error: (err) => {
        console.error('Error loading campaign:', err);
        this.error.set('Projekti laadimine ebaõnnestus');
        this.isLoading.set(false);
      }
    });
  }

  loadBidsWithAnalysis(id: string): void {
    this.isLoadingBids.set(true);
    this.rfqService.getCampaignBidsWithAnalysis(id).subscribe({
      next: (bids) => {
        this.bids.set(bids);
        this.isLoadingBids.set(false);
      },
      error: (err) => {
        console.error('Error loading bids:', err);
        this.isLoadingBids.set(false);
      }
    });
  }

  // Analysis
  loadComparison(campaignId: string): void {
    this.isLoadingComparison.set(true);
    this.analysisService.compareBids(campaignId).subscribe({
      next: (result) => {
        this.comparison.set(result);
        this.isLoadingComparison.set(false);
      },
      error: () => this.isLoadingComparison.set(false)
    });
  }

  loadNegotiation(bidId: string): void {
    this.isLoadingNegotiation.set(true);
    this.analysisService.getNegotiationStrategy(bidId).subscribe({
      next: (strategy) => {
        this.negotiation.set(strategy);
        this.isLoadingNegotiation.set(false);
      },
      error: () => this.isLoadingNegotiation.set(false)
    });
  }

  // Formatting helpers
  formatCurrency(value: number | null | undefined): string {
    if (value === null || value === undefined) return '-';
    return new Intl.NumberFormat('et-EE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('et-EE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  formatDateTime(dateStr: string | null): string {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('et-EE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] || category;
  }

  getCategoryIcon(category: string): string {
    return this.categoryIcons[category] || '📦';
  }

  getStepLabel(stepType: string): string {
    return this.stepLabels[stepType] || stepType;
  }

  getStepStatusClass(status: string): string {
    switch (status) {
      case 'COMPLETED': return 'step-completed';
      case 'RUNNING': return 'step-running';
      case 'FAILED': return 'step-failed';
      case 'SKIPPED': return 'step-skipped';
      default: return 'step-pending';
    }
  }

  getStepStatusIcon(status: string): string {
    switch (status) {
      case 'COMPLETED': return '✓';
      case 'RUNNING': return '⟳';
      case 'FAILED': return '✕';
      case 'SKIPPED': return '—';
      default: return '○';
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

  getPipelineStatusLabel(status: string): string {
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

  getPipelineStatusClass(status: string): string {
    switch (status) {
      case 'RUNNING': return 'status-active';
      case 'COMPLETED': return 'status-completed';
      case 'FAILED': return 'status-failed';
      case 'PAUSED': return 'status-quoting';
      default: return 'status-draft';
    }
  }

  getVerdictLabel(verdict: string | undefined): string {
    switch (verdict) {
      case 'GREAT_DEAL': return 'Suurepärane hind';
      case 'FAIR': return 'Aus hind';
      case 'OVERPRICED': return 'Ülehinnatad';
      case 'RED_FLAG': return 'Kahtlane';
      default: return 'Hindamata';
    }
  }

  getVerdictClass(verdict: string | undefined): string {
    switch (verdict) {
      case 'GREAT_DEAL': return 'verdict-great';
      case 'FAIR': return 'verdict-fair';
      case 'OVERPRICED': return 'verdict-over';
      case 'RED_FLAG': return 'verdict-red';
      default: return '';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'Aktiivne';
      case 'SENDING': return 'Saadan...';
      case 'COMPLETED': return 'Lõppenud';
      case 'CANCELLED': return 'Tühistatud';
      default: return status;
    }
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'status-active';
      case 'SENDING': return 'status-sending';
      case 'COMPLETED': return 'status-completed';
      case 'CANCELLED': return 'status-cancelled';
      default: return '';
    }
  }

  getBidRank(bid: Bid): number {
    const sorted = this.sortedBids();
    return sorted.findIndex(b => b.id === bid.id) + 1;
  }

  selectBid(bid: Bid): void {
    console.log('Selected bid:', bid);
  }

  // Validation helpers
  getConfidencePercent(confidence: number | undefined): number {
    return confidence != null ? Math.round(confidence * 100) : 0;
  }

  getConfidenceClass(confidence: number | undefined): string {
    if (confidence == null) return '';
    if (confidence >= 0.8) return 'confidence-high';
    if (confidence >= 0.6) return 'confidence-medium';
    return 'confidence-low';
  }

  getValidationIssues(issues: string | undefined): string[] {
    if (!issues) return [];
    return issues.split('; ');
  }

  // Supplier scoring helpers
  parseMatchedSuppliers(json: string | undefined): ScoredSupplier[] {
    if (!json) return [];
    try {
      return JSON.parse(json);
    } catch {
      return [];
    }
  }

  getScoreClass(score: number): string {
    if (score > 70) return 'score-high';
    if (score >= 40) return 'score-medium';
    return 'score-low';
  }

  // Procurement status helpers
  getProcurementStatusLabel(status: string | undefined): string {
    switch (status) {
      case 'ACTIVE': return 'Aktiivne';
      case 'DEFERRED': return 'Lükatud edasi';
      case 'COMPLETED': return 'Lõpetatud';
      default: return 'Aktiivne';
    }
  }

  getProcurementStatusClass(status: string | undefined): string {
    switch (status) {
      case 'ACTIVE': return 'procurement-active';
      case 'DEFERRED': return 'procurement-deferred';
      case 'COMPLETED': return 'procurement-completed';
      default: return '';
    }
  }

  isPipelinePausedForReview(): boolean {
    const p = this.pipeline();
    if (!p || p.status !== 'PAUSED') return false;
    const currentStep = p.steps[p.currentStep];
    return currentStep?.stepType === 'VALIDATE_PARSE';
  }
}
