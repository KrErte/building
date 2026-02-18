import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Pipeline } from '../models/project.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class PipelineService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  createPipeline(projectId: string): Observable<Pipeline> {
    return this.http.post<Pipeline>(`${this.apiUrl}/pipelines`, { projectId });
  }

  listPipelines(): Observable<Pipeline[]> {
    return this.http.get<Pipeline[]>(`${this.apiUrl}/pipelines`);
  }

  getProjectPipelines(projectId: string): Observable<Pipeline[]> {
    return this.http.get<Pipeline[]>(`${this.apiUrl}/pipelines/project/${projectId}`);
  }

  getPipeline(id: string): Observable<Pipeline> {
    return this.http.get<Pipeline>(`${this.apiUrl}/pipelines/${id}`);
  }

  resumePipeline(id: string): Observable<Pipeline> {
    return this.http.post<Pipeline>(`${this.apiUrl}/pipelines/${id}/resume`, {});
  }

  cancelPipeline(id: string): Observable<Pipeline> {
    return this.http.post<Pipeline>(`${this.apiUrl}/pipelines/${id}/cancel`, {});
  }
}
