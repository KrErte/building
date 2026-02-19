import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ComparisonResult, NegotiationStrategy, CompanyEnrichment, NegotiationRound } from '../models/project.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AnalysisService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  compareBids(campaignId: string): Observable<ComparisonResult> {
    return this.http.get<ComparisonResult>(`${this.apiUrl}/analysis/campaign/${campaignId}/compare`);
  }

  analyzeBid(bidId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/analysis/bid/${bidId}`);
  }

  getNegotiationStrategy(bidId: string): Observable<NegotiationStrategy> {
    return this.http.get<NegotiationStrategy>(`${this.apiUrl}/analysis/bid/${bidId}/negotiate`);
  }

  sendNegotiation(bidId: string, request: { targetPrice: number; message?: string; tone?: string }): Observable<NegotiationRound> {
    return this.http.post<NegotiationRound>(`${this.apiUrl}/analysis/bid/${bidId}/negotiate/send`, request);
  }

  getNegotiationRounds(bidId: string): Observable<NegotiationRound[]> {
    return this.http.get<NegotiationRound[]>(`${this.apiUrl}/analysis/bid/${bidId}/negotiate/rounds`);
  }

  getEnrichment(supplierId: string): Observable<CompanyEnrichment> {
    return this.http.get<CompanyEnrichment>(`${this.apiUrl}/enrichment/supplier/${supplierId}`);
  }

  triggerEnrichment(supplierId: string): Observable<CompanyEnrichment> {
    return this.http.post<CompanyEnrichment>(`${this.apiUrl}/enrichment/supplier/${supplierId}/enrich`, {});
  }

  getRiskAssessment(supplierId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/enrichment/supplier/${supplierId}/risk`);
  }
}
