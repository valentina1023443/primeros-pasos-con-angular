import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthSession {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  userId: number;
  username: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly url = `${environment.apiUrlAuth}/auth`;
  constructor(private readonly http: HttpClient) {}

  login(username: string, password: string): Observable<AuthSession> {
    return this.http.post<AuthSession>(`${this.url}/login`, { username, password }).pipe(
      tap(session => {
        localStorage.setItem('token', session.accessToken);
        localStorage.setItem('role', session.role);
        localStorage.setItem('userId', String(session.userId));
        localStorage.setItem('username', session.username);
        localStorage.setItem('tokenExpiresAt', session.expiresAt);
      })
    );
  }

  logout(): void {
    const token = localStorage.getItem('token');
    if (token) this.http.post(`${this.url}/logout`, {}, { headers: { Authorization: `Bearer ${token}` } }).subscribe({ error: () => undefined });
    ['token', 'role', 'userId', 'username', 'tokenExpiresAt'].forEach(key => localStorage.removeItem(key));
  }

  isAuthenticated(): boolean {
    const token = localStorage.getItem('token');
    const expires = localStorage.getItem('tokenExpiresAt');
    return !!token && !!expires && Date.parse(expires) > Date.now();
  }

  hasRole(...roles: string[]): boolean {
    return roles.includes(localStorage.getItem('role') || '');
  }
}
