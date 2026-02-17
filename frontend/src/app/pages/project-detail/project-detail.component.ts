import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { RfqService } from '../../services/rfq.service';
import { Campaign, Bid } from '../../models/rfq.model';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './project-detail.component.html',
  styleUrls: ['./project-detail.component.scss']
})
export class ProjectDetailComponent implements OnInit {
  campaignId = signal<string>('');
  campaign = signal<Campaign | null>(null);
  bids = signal<Bid[]>([]);
  isLoading = signal(true);
  isLoadingBids = signal(false);
  error = signal<string | null>(null);

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
    private rfqService: RfqService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.campaignId.set(id);
      this.loadCampaign(id);
    } else {
      this.error.set('Projekti ID puudub');
      this.isLoading.set(false);
    }
  }

  loadCampaign(id: string): void {
    this.rfqService.getCampaign(id).subscribe({
      next: (campaign) => {
        this.campaign.set(campaign);
        this.isLoading.set(false);
        this.loadBidsWithAnalysis(id);
      },
      error: (err) => {
        console.error('Error loading campaign:', err);
        this.error.set('Projekti laadimine ebaonnestus');
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

  getVerdictLabel(verdict: string | undefined): string {
    switch (verdict) {
      case 'GREAT_DEAL': return 'Suureparane hind';
      case 'FAIR': return 'Aus hind';
      case 'OVERPRICED': return 'Ulehinnatad';
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
      case 'COMPLETED': return 'Loppenud';
      case 'CANCELLED': return 'Tuhistatud';
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
    // TODO: Implement bid selection/acceptance
  }
}
