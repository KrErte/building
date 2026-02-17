export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  company?: string;
  phone?: string;
  role: 'USER' | 'ADMIN';
  plan: 'FREE' | 'PRO' | 'ENTERPRISE';
  emailVerified: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  company?: string;
  phone?: string;
}

export interface AuthResponse {
  user: User;
  token: string;
  message?: string;
}

export interface AuthError {
  error: string;
  message: string;
}
