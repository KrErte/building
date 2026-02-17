export interface MaterialLine {
  name: string;
  quantity: number;
  unit: string;
  unitPriceMin: number;
  unitPriceMax: number;
  supplierName: string;
  supplierUrl: string;
  priceSource: 'AUTO' | 'MANUAL';
  lastUpdated: string;
}

export interface LaborCost {
  hoursEstimate: number;
  hourlyRateMin: number;
  hourlyRateMax: number;
  totalMin: number;
  totalMax: number;
  source: string;
}

export interface OtherCosts {
  transportMin: number;
  transportMax: number;
  wasteDisposalMin: number;
  wasteDisposalMax: number;
  totalMin: number;
  totalMax: number;
}

export interface PriceBreakdown {
  materials: MaterialLine[];
  labor: LaborCost;
  otherCosts: OtherCosts;
  confidencePercent: number;
  confidenceLabel: string;
  totalMin: number;
  totalMax: number;
}

export interface SupplierPrice {
  supplierName: string;
  price: number;
  url: string;
  lastUpdated: string;
}
