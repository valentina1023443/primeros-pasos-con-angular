import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class HeadersInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = typeof localStorage !== 'undefined' ? localStorage.getItem('token') : null;
    const isMultipart = typeof FormData !== 'undefined' && req.body instanceof FormData;
    let headers = req.headers;
    if (!isMultipart && !headers.has('Content-Type') && req.body !== null && req.body !== undefined) {
      headers = headers.set('Content-Type', 'application/json');
    }
    if (token && !headers.has('Authorization') && !req.url.includes('/auth/login')) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return next.handle(req.clone({ headers }));
  }
}
