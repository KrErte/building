import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Organization } from '../models/user.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class OrganizationService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getMyOrganizations(): Observable<Organization[]> {
    return this.http.get<Organization[]>(`${this.apiUrl}/organizations`);
  }

  getOrganization(id: string): Observable<Organization> {
    return this.http.get<Organization>(`${this.apiUrl}/organizations/${id}`);
  }

  createOrganization(data: { name: string; slug: string }): Observable<Organization> {
    return this.http.post<Organization>(`${this.apiUrl}/organizations`, data);
  }

  addMember(orgId: string, email: string, role: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/organizations/${orgId}/members`, { email, role });
  }

  removeMember(orgId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/organizations/${orgId}/members/${userId}`);
  }

  switchOrganization(orgId: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/organizations/${orgId}/switch`, {});
  }
}
