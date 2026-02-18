import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  // Skip auth header for public endpoints
  const publicPaths = ['/auth/login', '/auth/register', '/auth/refresh', '/bids/page/', '/bids/submit/'];
  const isPublic = publicPaths.some(path => req.url.includes(path));

  let authReq = req;
  if (token && !isPublic) {
    authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isPublic && !req.url.includes('/auth/refresh')) {
        return authService.refreshToken().pipe(
          switchMap(() => {
            const newToken = authService.getToken();
            const retryReq = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` }
            });
            return next(retryReq);
          }),
          catchError(refreshError => throwError(() => refreshError))
        );
      }
      return throwError(() => error);
    })
  );
};
