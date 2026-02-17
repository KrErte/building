export interface ProjectStage {
  name: string;
  category: string;
  quantity: number;
  unit: string;
  description: string;
  dependencies: string[];
  priceEstimateMin: number;
  priceEstimateMax: number;
  priceEstimateMedian: number;
  supplierCount: number;
  selected?: boolean;
  expanded?: boolean;
}

export interface ProjectParseResult {
  projectTitle: string;
  location: string;
  totalBudget: number | null;
  deadline: string | null;
  stages: ProjectStage[];
  totalEstimateMin: number;
  totalEstimateMax: number;
  totalSupplierCount: number;
}

export interface ProjectParseRequest {
  description: string;
}

export const CATEGORY_LABELS: { [key: string]: string } = {
  'GENERAL_CONSTRUCTION': 'Üldehitus',
  'ELECTRICAL': 'Elektritööd',
  'PLUMBING': 'Sanitaartehnilised tööd',
  'TILING': 'Plaatimistööd',
  'FINISHING': 'Viimistlustööd',
  'ROOFING': 'Katuse tööd',
  'FACADE': 'Fassaaditööd',
  'LANDSCAPING': 'Haljastus',
  'DEMOLITION': 'Lammutustööd',
  'FLOORING': 'Põrandatööd',
  'HVAC': 'Küte ja ventilatsioon',
  'WINDOWS_DOORS': 'Aknad ja uksed',
  'OTHER': 'Muud tööd'
};

export const CATEGORY_ICONS: { [key: string]: string } = {
  'GENERAL_CONSTRUCTION': '🏗️',
  'ELECTRICAL': '⚡',
  'PLUMBING': '🔧',
  'TILING': '🔲',
  'FINISHING': '🎨',
  'ROOFING': '🏠',
  'FACADE': '🏢',
  'LANDSCAPING': '🌳',
  'DEMOLITION': '🔨',
  'FLOORING': '🪵',
  'HVAC': '❄️',
  'WINDOWS_DOORS': '🪟',
  'OTHER': '📦'
};
