import { Injectable } from '@angular/core';
import { HttpClient, HttpEventType, HttpEvent, HttpRequest } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { ProjectParseResult, ProjectParseRequest } from '../models/project.model';
import { environment } from '../../environments/environment';

export interface UploadProgress {
  phase: 'uploading' | 'processing';
  progress: number; // 0-100
  loaded: number; // bytes
  total: number; // bytes
  speed: number; // bytes per second
  remainingTime: number; // seconds
  processingStep?: string;
  processingSteps?: ProcessingStep[];
}

export interface ProcessingStep {
  label: string;
  status: 'pending' | 'active' | 'done';
}

@Injectable({
  providedIn: 'root'
})
export class ProjectService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  parseFromText(description: string): Observable<ProjectParseResult> {
    const request: ProjectParseRequest = { description };
    return this.http.post<ProjectParseResult>(`${this.apiUrl}/projects/parse`, request);
  }

  parseFromFile(file: File): Observable<ProjectParseResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ProjectParseResult>(`${this.apiUrl}/projects/parse-file`, formData);
  }

  /**
   * Upload file with progress tracking
   */
  parseFromFileWithProgress(
    file: File,
    progressCallback: (progress: UploadProgress) => void
  ): Observable<ProjectParseResult> {
    return new Observable(observer => {
      const formData = new FormData();
      formData.append('file', file);

      const xhr = new XMLHttpRequest();
      let startTime = Date.now();
      let lastLoaded = 0;
      let lastTime = startTime;

      // Processing steps for different file types
      const isZip = file.name.toLowerCase().endsWith('.zip');
      const isIfc = file.name.toLowerCase().endsWith('.ifc');
      const processingSteps: ProcessingStep[] = isZip ? [
        { label: `Fail üles laaditud (${this.formatFileSize(file.size)})`, status: 'pending' },
        { label: 'Pakkin lahti...', status: 'pending' },
        { label: 'Analüüsin faile...', status: 'pending' },
        { label: 'Arvutan hinda...', status: 'pending' },
        { label: 'Otsin tegijaid...', status: 'pending' }
      ] : isIfc ? [
        { label: `Fail üles laaditud (${this.formatFileSize(file.size)})`, status: 'pending' },
        { label: 'Analüüsin BIM mudelit...', status: 'pending' },
        { label: 'Tuvastan ehituselemente...', status: 'pending' },
        { label: 'Arvutan hinda...', status: 'pending' },
        { label: 'Otsin tegijaid...', status: 'pending' }
      ] : [
        { label: `Fail üles laaditud (${this.formatFileSize(file.size)})`, status: 'pending' },
        { label: 'Analüüsin dokumenti...', status: 'pending' },
        { label: 'Arvutan hinda...', status: 'pending' },
        { label: 'Otsin tegijaid...', status: 'pending' }
      ];

      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable) {
          const now = Date.now();
          const timeDiff = (now - lastTime) / 1000; // seconds
          const bytesDiff = event.loaded - lastLoaded;

          // Calculate speed (smoothed)
          const instantSpeed = timeDiff > 0 ? bytesDiff / timeDiff : 0;
          const totalTime = (now - startTime) / 1000;
          const averageSpeed = totalTime > 0 ? event.loaded / totalTime : 0;
          const speed = averageSpeed * 0.7 + instantSpeed * 0.3; // Weighted average

          // Calculate remaining time
          const remaining = event.total - event.loaded;
          const remainingTime = speed > 0 ? remaining / speed : 0;

          const progress = Math.round((event.loaded / event.total) * 100);

          progressCallback({
            phase: 'uploading',
            progress,
            loaded: event.loaded,
            total: event.total,
            speed,
            remainingTime,
            processingSteps
          });

          lastLoaded = event.loaded;
          lastTime = now;
        }
      });

      xhr.upload.addEventListener('load', () => {
        // Upload complete, now processing
        processingSteps[0].status = 'done';
        processingSteps[1].status = 'active';

        progressCallback({
          phase: 'processing',
          progress: 100,
          loaded: file.size,
          total: file.size,
          speed: 0,
          remainingTime: 0,
          processingStep: processingSteps[1].label,
          processingSteps
        });

        // Animate processing steps (fast animation, actual work happens in backend)
        let stepIndex = 1;
        const stepInterval = setInterval(() => {
          if (stepIndex < processingSteps.length - 1) {
            processingSteps[stepIndex].status = 'done';
            stepIndex++;
            processingSteps[stepIndex].status = 'active';

            progressCallback({
              phase: 'processing',
              progress: 100,
              loaded: file.size,
              total: file.size,
              speed: 0,
              remainingTime: 0,
              processingStep: processingSteps[stepIndex].label,
              processingSteps
            });
          }
        }, 400); // Fast animation - 400ms per step

        // Store interval to clear later
        (xhr as any)._stepInterval = stepInterval;
      });

      xhr.addEventListener('load', () => {
        if ((xhr as any)._stepInterval) {
          clearInterval((xhr as any)._stepInterval);
        }

        if (xhr.status >= 200 && xhr.status < 300) {
          // Mark all steps as done
          processingSteps.forEach(s => s.status = 'done');
          progressCallback({
            phase: 'processing',
            progress: 100,
            loaded: file.size,
            total: file.size,
            speed: 0,
            remainingTime: 0,
            processingSteps
          });

          try {
            const result = JSON.parse(xhr.responseText);
            observer.next(result);
            observer.complete();
          } catch (e) {
            observer.error(new Error('Vastuse töötlemine ebaõnnestus'));
          }
        } else {
          observer.error(new Error(`Üleslaadimine ebaõnnestus: ${xhr.status}`));
        }
      });

      xhr.addEventListener('error', () => {
        if ((xhr as any)._stepInterval) {
          clearInterval((xhr as any)._stepInterval);
        }
        observer.error(new Error('Võrguühenduse viga'));
      });

      xhr.addEventListener('timeout', () => {
        if ((xhr as any)._stepInterval) {
          clearInterval((xhr as any)._stepInterval);
        }
        observer.error(new Error('Ühendus aegus'));
      });

      xhr.open('POST', `${this.apiUrl}/projects/parse-file`);
      xhr.timeout = 30 * 60 * 1000; // 30 minutes for large files
      xhr.send(formData);

      // Return cleanup function
      return () => {
        if ((xhr as any)._stepInterval) {
          clearInterval((xhr as any)._stepInterval);
        }
        xhr.abort();
      };
    });
  }

  private formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
  }
}
