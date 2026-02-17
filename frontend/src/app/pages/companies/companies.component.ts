import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CompanyService } from '../../services/company.service';
import { Company, CompanyPageResponse, SOURCE_LABELS, SOURCE_COLORS, CATEGORY_LABELS } from '../../models/company.model';

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './companies.component.html',
  styleUrls: ['./companies.component.scss']
})
export class CompaniesComponent implements OnInit {
  companies = signal<Company[]>([]);
  isLoading = signal(false);
  error = signal<string | null>(null);

  // Pagination
  currentPage = signal(0);
  pageSize = signal(25);
  totalElements = signal(0);
  totalPages = signal(0);
  hasNext = signal(false);
  hasPrevious = signal(false);

  // Search and Sort
  searchQuery = signal('');
  sortBy = signal('name');
  sortDir = signal<'asc' | 'desc'>('asc');

  sourceLabels = SOURCE_LABELS;
  sourceColors = SOURCE_COLORS;

  constructor(private companyService: CompanyService) {}

  ngOnInit(): void {
    this.loadCompanies();
  }

  loadCompanies(): void {
    this.isLoading.set(true);
    this.error.set(null);

    this.companyService.getCompanies(
      this.currentPage(),
      this.pageSize(),
      this.searchQuery(),
      this.sortBy(),
      this.sortDir()
    ).subscribe({
      next: (response: CompanyPageResponse) => {
        this.companies.set(response.companies);
        this.totalElements.set(response.totalElements);
        this.totalPages.set(response.totalPages);
        this.hasNext.set(response.hasNext);
        this.hasPrevious.set(response.hasPrevious);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Error loading companies:', err);
        this.error.set('Ettevõtete laadimine ebaõnnestus');
        this.isLoading.set(false);
      }
    });
  }

  onSearch(): void {
    this.currentPage.set(0);
    this.loadCompanies();
  }

  onSort(column: string): void {
    if (this.sortBy() === column) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortBy.set(column);
      this.sortDir.set('asc');
    }
    this.loadCompanies();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) {
      this.currentPage.set(page);
      this.loadCompanies();
    }
  }

  nextPage(): void {
    if (this.hasNext()) {
      this.currentPage.update(p => p + 1);
      this.loadCompanies();
    }
  }

  prevPage(): void {
    if (this.hasPrevious()) {
      this.currentPage.update(p => p - 1);
      this.loadCompanies();
    }
  }

  getSourceLabel(source: string): string {
    return this.sourceLabels[source] || source;
  }

  getSourceColor(source: string): string {
    return this.sourceColors[source] || '#666666';
  }

  getCategoryLabel(category: string): string {
    return CATEGORY_LABELS[category] || category;
  }

  getPageNumbers(): number[] {
    const total = this.totalPages();
    const current = this.currentPage();
    const pages: number[] = [];

    const start = Math.max(0, current - 2);
    const end = Math.min(total - 1, current + 2);

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }

    return pages;
  }

  clearSearch(): void {
    this.searchQuery.set('');
    this.onSearch();
  }

  getEndItem(): number {
    return Math.min((this.currentPage() + 1) * this.pageSize(), this.totalElements());
  }
}
