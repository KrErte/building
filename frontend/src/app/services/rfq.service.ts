import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BidPage, BidSubmission, RfqRequest, Campaign, Bid } from '../models/rfq.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class RfqService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  sendRfq(request: RfqRequest): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.apiUrl}/rfq/send`, request);
  }

  getBidPage(token: string): Observable<BidPage> {
    return this.http.get<BidPage>(`${this.apiUrl}/bids/page/${token}`);
  }

  submitBid(token: string, bid: BidSubmission): Observable<Bid> {
    return this.http.post<Bid>(`${this.apiUrl}/bids/submit/${token}`, bid);
  }

  getCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(`${this.apiUrl}/campaigns`);
  }

  getCampaign(id: string): Observable<Campaign> {
    return this.http.get<Campaign>(`${this.apiUrl}/campaigns/${id}`);
  }

  getAllBids(): Observable<Bid[]> {
    return this.http.get<Bid[]>(`${this.apiUrl}/bids`);
  }

  getCampaignBidsWithAnalysis(campaignId: string): Observable<Bid[]> {
    return this.http.get<Bid[]>(`${this.apiUrl}/rfq/${campaignId}/bids`);
  }
}
