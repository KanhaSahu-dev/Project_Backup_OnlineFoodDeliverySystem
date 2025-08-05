import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { jwtDecode } from 'jwt-decode';
import { ToastrService } from 'ngx-toastr';
import { catchError, finalize, tap } from 'rxjs/operators';

export interface User {
  id: number;
  name: string;
  email: string;
  phone?: string | number;  // Support both string and number from backend
  address?: string;
  userType?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  phone: string;
  address: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:9095/api';
  private userSubject = new BehaviorSubject<User | null>(null);
  private userIdSubject = new BehaviorSubject<number | null>(this.getUserIdFromToken());
  private loadingSubject = new BehaviorSubject<boolean>(false);

  public user$ = this.userSubject.asObservable();
  public userId$ = this.userIdSubject.asObservable();
  public loading$ = this.loadingSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
    private toastr: ToastrService
  ) {
    this.initializeUser();
  }

  private getToken(): string | null {
    if (typeof window === 'undefined') return null;
    try {
      return localStorage.getItem('jwt');
    } catch {
      return null;
    }
  }

  getCurrentUserRoles(): string[] {
  const token = this.getToken();
  if (!token) return [];

  try {
    const decoded: any = jwtDecode(token);
    const roles = decoded.roles || decoded.userType || [];
    return Array.isArray(roles) ? roles : [roles];
  } catch (error) {
    console.error('Error decoding roles from token:', error);
    return [];
  }
}


  private getUserIdFromToken(): number | null {
    try {
      const token = this.getToken();
      if (!token) return null;
      const decoded: any = jwtDecode(token);
      return decoded.userId || null;
    } catch (error) {
      console.error('Error decoding token:', error);
      return null;
    }
  }

  private initializeUser(): void {
    const token = this.getToken();
    if (token) {
      const userId = this.getUserIdFromToken();
      if (userId) {
        this.userIdSubject.next(userId);
        // Only fetch profile for customers, not restaurants
        const roles = this.getCurrentUserRoles();
        if (roles.includes('CUSTOMER')) {
        this.fetchUserProfile().subscribe();
        }
      }
    }
  }

  customerLogin(credentials: LoginRequest): Observable<any> {
    this.loadingSubject.next(true);
    // Clear any existing expired data before login
    this.clearExpiredData();
    
    return this.http.post(`${this.apiUrl}/auth/customer/login`, credentials).pipe(
      tap((response: any) => {
        const token = response.jwt || response.jwtToken;
        if (token) {
          localStorage.setItem('jwt', token);
          const userId = this.getUserIdFromToken();
          this.userIdSubject.next(userId);
          this.toastr.success('Login successful!');
          this.router.navigate(['/main']);
        } else {
          this.toastr.error('Invalid response from server');
          throw new Error('Invalid response');
        }
      }),
      catchError(error => {
        console.error('Login error:', error);
        if (error.status === 0) {
          this.toastr.error('Cannot connect to server. Please check if the backend is running.');
        } else {
          this.toastr.error(error.error?.message || error.message || 'Login failed');
        }
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  restaurantLogin(credentials: LoginRequest): Observable<any> {
    this.loadingSubject.next(true);
    // Clear any existing expired data before login
    this.clearExpiredData();
    
    return this.http.post(`${this.apiUrl}/auth/restaurant/login`, credentials).pipe(
      tap((response: any) => {
        const token = response.jwt || response.jwtToken;
        if (token) {
          localStorage.setItem('jwt', token);
          const userId = this.getUserIdFromToken();
          this.userIdSubject.next(userId);
          this.toastr.success('Login successful!');
          this.router.navigate(['/restaurant/dashboard']);
        } else {
          this.toastr.error('Invalid response from server');
          throw new Error('Invalid response');
        }
      }),
      catchError(error => {
        console.error('Restaurant login error:', error);
        if (error.status === 0) {
          this.toastr.error('Cannot connect to server. Please check if the backend is running.');
        } else {
          this.toastr.error(error.error?.message || error.message || 'Login failed');
        }
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  customerRegister(data: RegisterRequest): Observable<any> {
    this.loadingSubject.next(true);
    return this.http.post(`${this.apiUrl}/customers/register`, data).pipe(
      tap((response: any) => {
        this.toastr.success('Registration successful! Please login.');
        this.router.navigate(['/customer/login']);
      }),
      catchError(error => {
        console.error('Customer registration error:', error);
        this.toastr.error(error.error?.message || error.message || 'Registration failed');
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  restaurantRegister(data: any): Observable<any> {
    this.loadingSubject.next(true);
    return this.http.post(`${this.apiUrl}/restaurants/register`, data).pipe(
      tap((response: any) => {
        this.toastr.success('Registration successful! Please login.');
        this.router.navigate(['/restaurant/login']);
      }),
      catchError(error => {
        console.error('Restaurant registration error:', error);
        this.toastr.error(error.error?.message || error.message || 'Registration failed');
        return throwError(() => error);
      }),
      finalize(() => this.loadingSubject.next(false))
    );
  }

  fetchUserProfile(): Observable<User> {
    const userId = this.userIdSubject.value;
    if (!userId) {
      return throwError(() => new Error('No user ID available'));
    }

    // Get the token to include user roles
    const token = this.getToken();
    if (!token) {
      return throwError(() => new Error('No authentication token available'));
    }

    const headers = new HttpHeaders({
      ...this.getAuthHeaders(),
      'X-Internal-User-Id': userId.toString(),
      'X-Internal-User-Roles': 'CUSTOMER'
    });

    return this.http.get<User>(`${this.apiUrl}/customers/user`, {
      headers: headers
    }).pipe(
      tap((user: User) => {
        console.log('✅ User profile fetched from backend:', user);
        this.userSubject.next(user);
      }),
      catchError(error => {
        console.error('❌ Error fetching user profile:', error);
        return throwError(() => error);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('jwt');
    this.userSubject.next(null);
    this.userIdSubject.next(null);
    this.toastr.success('Logged out successfully');
    this.router.navigate(['/']);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const decoded: any = jwtDecode(token);
      const isValid = decoded.exp * 1000 > Date.now();
      if (!isValid) {
        this.clearExpiredData();
      }
      return isValid;
    } catch {
      this.clearExpiredData();
      return false;
    }
  }

  private clearExpiredData(): void {
    localStorage.removeItem('jwt');
    localStorage.removeItem('cart');
    localStorage.removeItem('cartRestaurant');
    this.userSubject.next(null);
    this.userIdSubject.next(null);
  }

  getCurrentUser(): User | null {
    return this.userSubject.value;
  }

  getCurrentUserId(): number | null {
    return this.userIdSubject.value;
  }

  getAuthHeaders(): { [key: string]: string } {
    const token = this.getToken();
    if (token) {
      return { Authorization: `Bearer ${token}` };
    }
    return {};
  }
}
