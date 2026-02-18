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
  refreshToken?: string;
  expiresIn?: number;
  message?: string;
}

export interface AuthError {
  error: string;
  message: string;
}

export interface Organization {
  id: string;
  name: string;
  slug: string;
  plan: string;
  ownerId: string;
  maxMembers: number;
  maxProjectsPerMonth: number;
  members: OrganizationMember[];
  createdAt: string;
}

export interface OrganizationMember {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
}
