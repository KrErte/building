import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CompanyPageResponse } from '../models/company.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CompanyService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getCompanies(
    page: number = 0,
    size: number = 25,
    search: string = '',
    sort: string = 'name',
    dir: string = 'asc'
  ): Observable<CompanyPageResponse> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort)
      .set('dir', dir);

    if (search) {
      params = params.set('search', search);
    }

    return this.http.get<CompanyPageResponse>(`${this.apiUrl}/companies`, { params });
  }

  getCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/companies/count`);
  }

  getCompaniesByCategory(category: string, location: string = '', limit: number = 10): Observable<CompanyPageResponse> {
    let params = new HttpParams()
      .set('page', '0')
      .set('size', limit.toString())
      .set('category', category);

    if (location && location.trim()) {
      params = params.set('city', location);
    }

    return this.http.get<CompanyPageResponse>(`${this.apiUrl}/companies`, { params });
  }
}
