export interface BidPage {
  token: string;
  campaignTitle: string;
  category: string;
  location: string;
  quantity: number | null;
  unit: string;
  specifications: string;
  deadline: string | null;
  maxBudget: number | null;
  supplierName: string;
  supplierEmail: string;
  alreadySubmitted: boolean;
  existingBidPrice: string | null;
  existingBidNotes: string | null;
}

export interface BidSubmission {
  price: number;
  timelineDays: number | null;
  deliveryDate: string | null;
  notes: string;
  lineItems: string | null;
}

export interface RfqRequest {
  title: string;
  category: string;
  location: string;
  quantity: number;
  unit: string;
  specifications: string;
  maxBudget: number | null;
  deadline: string | null;
  supplierIds: string[];
}

export interface Campaign {
  id: string;
  title: string;
  category: string;
  location: string;
  quantity: number;
  unit: string;
  specifications: string;
  deadline: string | null;
  maxBudget: number | null;
  status: string;
  totalSent: number;
  totalResponded: number;
  createdAt: string;
  bids?: Bid[];
}

export interface Bid {
  id: string;
  supplierName: string;
  supplierEmail: string;
  price: number;
  currency: string;
  timelineDays: number | null;
  deliveryDate: string | null;
  notes: string;
  status: string;
  submittedAt: string;
  // AI Analysis fields
  percentFromMedian?: number;
  verdict?: 'GREAT_DEAL' | 'FAIR' | 'OVERPRICED' | 'RED_FLAG';
  marketPricePerUnit?: number;
}

export interface MarketPriceEstimate {
  category: string;
  minPrice: number;
  maxPrice: number;
  avgPrice: number;
  medianPrice: number;
  unit: string;
  sampleCount: number;
  estimatedTotal?: {
    min: number;
    max: number;
    avg: number;
  };
}

export interface WizardSupplier {
  id: string;
  companyName: string;
  contactPerson?: string;
  email: string;
  phone?: string;
  city: string;
  categories: string[];
  googleRating?: number;
  googleReviewCount?: number;
  trustScore?: number;
  isVerified: boolean;
  selected?: boolean;
}
