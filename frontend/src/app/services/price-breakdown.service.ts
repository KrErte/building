import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, tap, shareReplay } from 'rxjs/operators';
import { PriceBreakdown, SupplierPrice } from '../models/price-breakdown.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PriceBreakdownService {
  private apiUrl = environment.apiUrl;

  // Cache for price breakdowns (category_quantity_unit -> Observable)
  private breakdownCache = new Map<string, Observable<PriceBreakdown>>();

  // Cache for supplier prices (materialName_region -> Observable)
  private supplierCache = new Map<string, Observable<SupplierPrice[]>>();

  constructor(private http: HttpClient) {}

  getPriceBreakdown(category: string, quantity: number, unit: string = 'm2'): Observable<PriceBreakdown> {
    const cacheKey = `${category}_${quantity}_${unit}`;

    // Return cached observable if available
    if (this.breakdownCache.has(cacheKey)) {
      return this.breakdownCache.get(cacheKey)!;
    }

    // Create new request with shareReplay to cache the result
    const request$ = this.http.get<PriceBreakdown>(`${this.apiUrl}/prices/breakdown`, {
      params: { category, quantity: quantity.toString(), unit }
    }).pipe(
      catchError(err => {
        console.error('Failed to fetch price breakdown:', err);
        // Remove from cache on error so it can be retried
        this.breakdownCache.delete(cacheKey);
        return of(this.getDefaultBreakdown());
      }),
      shareReplay(1)
    );

    this.breakdownCache.set(cacheKey, request$);
    return request$;
  }

  getSupplierPrices(materialName: string, region: string = 'estonia'): Observable<SupplierPrice[]> {
    const cacheKey = `${materialName}_${region}`;

    // Return cached observable if available
    if (this.supplierCache.has(cacheKey)) {
      return this.supplierCache.get(cacheKey)!;
    }

    const request$ = this.http.get<SupplierPrice[]>(
      `${this.apiUrl}/materials/${encodeURIComponent(materialName)}/suppliers`,
      { params: { region } }
    ).pipe(
      catchError(err => {
        console.error('Failed to fetch supplier prices:', err);
        this.supplierCache.delete(cacheKey);
        return of([]);
      }),
      shareReplay(1)
    );

    this.supplierCache.set(cacheKey, request$);
    return request$;
  }

  // Clear cache (useful for testing or when data needs refresh)
  clearCache(): void {
    this.breakdownCache.clear();
    this.supplierCache.clear();
  }

  private getDefaultBreakdown(): PriceBreakdown {
    return {
      materials: [],
      labor: {
        hoursEstimate: 0,
        hourlyRateMin: 25,
        hourlyRateMax: 45,
        totalMin: 0,
        totalMax: 0,
        source: 'Hinnangud pole saadaval'
      },
      otherCosts: {
        transportMin: 0,
        transportMax: 0,
        wasteDisposalMin: 0,
        wasteDisposalMax: 0,
        totalMin: 0,
        totalMax: 0
      },
      confidencePercent: 0,
      confidenceLabel: 'Andmed puuduvad',
      totalMin: 0,
      totalMax: 0
    };
  }
}
