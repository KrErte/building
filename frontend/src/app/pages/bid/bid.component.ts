import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { RfqService } from '../../services/rfq.service';
import { BidPage, BidSubmission } from '../../models/rfq.model';

@Component({
  selector: 'app-bid',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bid.component.html',
  styleUrls: ['./bid.component.scss']
})
export class BidComponent implements OnInit {
  token = signal<string>('');
  bidPage = signal<BidPage | null>(null);
  isLoading = signal(true);
  error = signal<string | null>(null);
  isSubmitting = signal(false);
  isSubmitted = signal(false);

  // Form fields
  price = signal<number | null>(null);
  timelineDays = signal<number | null>(null);
  deliveryDate = signal<string>('');
  notes = signal<string>('');

  constructor(
    private route: ActivatedRoute,
    private rfqService: RfqService
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');
    if (token) {
      this.token.set(token);
      this.loadBidPage(token);
    } else {
      this.error.set('Vigane link');
      this.isLoading.set(false);
    }
  }

  loadBidPage(token: string): void {
    this.rfqService.getBidPage(token).subscribe({
      next: (page) => {
        this.bidPage.set(page);
        this.isLoading.set(false);
        if (page.alreadySubmitted) {
          this.isSubmitted.set(true);
        }
      },
      error: (err) => {
        console.error('Error loading bid page:', err);
        this.error.set('Hinnaparingut ei leitud voi link on aegunud');
        this.isLoading.set(false);
      }
    });
  }

  submitBid(): void {
    const priceVal = this.price();
    if (!priceVal || priceVal <= 0) {
      this.error.set('Palun sisesta kehtiv hind');
      return;
    }

    this.error.set(null);
    this.isSubmitting.set(true);

    const submission: BidSubmission = {
      price: priceVal,
      timelineDays: this.timelineDays(),
      deliveryDate: this.deliveryDate() || null,
      notes: this.notes(),
      lineItems: null
    };

    this.rfqService.submitBid(this.token(), submission).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.isSubmitted.set(true);
      },
      error: (err) => {
        console.error('Error submitting bid:', err);
        this.error.set(err.error?.error || 'Pakkumise esitamine ebaonnestus');
        this.isSubmitting.set(false);
      }
    });
  }

  formatCurrency(value: number | null): string {
    if (value === null) return '-';
    return new Intl.NumberFormat('et-EE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  }
}
