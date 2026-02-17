export interface Company {
  id: string;
  companyName: string;
  contactPerson: string | null;
  email: string | null;
  phone: string | null;
  website: string | null;
  address: string | null;
  city: string | null;
  county: string | null;
  categories: string[];
  serviceAreas: string[];
  source: string;
  googleRating: number | null;
  googleReviewCount: number | null;
  trustScore: number | null;
  isVerified: boolean;
}

export interface CompanyPageResponse {
  companies: Company[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const SOURCE_LABELS: { [key: string]: string } = {
  'GOOGLE_PLACES': 'Google',
  'FIE_REGISTER': 'FIE Register',
  'VERIFIED': 'Verified',
  'PROCUREMENT': 'Procurement',
  'SEED': 'Seed Data',
  'BUSINESS_REGISTRY': 'Äriregister'
};

export const SOURCE_COLORS: { [key: string]: string } = {
  'GOOGLE_PLACES': '#4285f4',
  'FIE_REGISTER': '#34a853',
  'VERIFIED': '#8b5cf6',
  'PROCUREMENT': '#fbbc05',
  'SEED': '#666666',
  'BUSINESS_REGISTRY': '#0ea5e9'
};

export const CATEGORY_LABELS: { [key: string]: string } = {
  'GENERAL_CONSTRUCTION': 'Uldehitus',
  'ELECTRICAL': 'Elektritood',
  'PLUMBING': 'Sanitaartehnilised tood',
  'TILING': 'Plaatimistood',
  'FINISHING': 'Viimistlustood',
  'ROOFING': 'Katuse tood',
  'FACADE': 'Fassaaditood',
  'LANDSCAPING': 'Haljastus',
  'DEMOLITION': 'Lammutustood',
  'FLOORING': 'Porandatood',
  'HVAC': 'Kute ja ventilatsioon',
  'WINDOWS_DOORS': 'Aknad ja uksed',
  'OTHER': 'Muud tood'
};
