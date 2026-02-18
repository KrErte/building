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

export interface PipeComponent {
  type: string;
  typeLabel: string;
  diameterMm: number;
  count: number;
  lengthM: number | null;
  material: string;
  confidence: number;
  unitPriceMin: number;
  unitPriceMax: number;
  totalPriceMin: number;
  totalPriceMax: number;
  priceUnit: string;
}

export interface PipeSystem {
  systemCode: string;
  systemName: string;
  systemType: string;
  lengthMeters: number;
  pipeSpecs: string;
  confidence: number;
  components: PipeComponent[];
  totalPriceMin: number;
  totalPriceMax: number;
  expanded?: boolean;
}

export interface DependentMaterial {
  materialName: string;
  totalQuantity: number;
  unit: string;
  unitPriceMin: number;
  unitPriceMax: number;
  totalPriceMin: number;
  totalPriceMax: number;
  sourceStages: string[];
}

export interface MaterialSummary {
  name: string;
  type: 'WORK' | 'MATERIAL';
  quantity: number;
  unit: string;
  priceMin: number;
  priceMax: number;
  category: string;
}

export interface ProjectParseResult {
  projectTitle: string;
  location: string;
  totalBudget: number | null;
  deadline: string | null;
  stages: ProjectStage[];
  pipeSystems: PipeSystem[] | null;
  totalEstimateMin: number;
  totalEstimateMax: number;
  totalSupplierCount: number;
  dependentMaterials: DependentMaterial[] | null;
  summary: MaterialSummary[] | null;
  materialsTotalMin: number;
  materialsTotalMax: number;
  grandTotalMin: number;
  grandTotalMax: number;
}

export interface ProjectParseRequest {
  description: string;
}

// Persisted project (from Phase 1B)
export interface Project {
  id: string;
  title: string;
  location: string;
  budget: number | null;
  status: 'DRAFT' | 'PARSED' | 'QUOTING' | 'COMPLETED' | 'ARCHIVED';
  description: string;
  deadline: string | null;
  totalEstimateMin: number;
  totalEstimateMax: number;
  totalSupplierCount: number;
  stages: ProjectStageDto[];
  createdAt: string;
  updatedAt: string;
  // Phase 1: Validation fields
  parseConfidence?: number;
  validationStatus?: string;
  // Phase 4: Timeline fields
  quotingHorizonDays?: number;
  constructionStartDate?: string;
}

export interface ScoredSupplier {
  supplierId: string;
  companyName: string;
  email: string;
  totalScore: number;
  categoryScore: number;
  locationScore: number;
  responseScore: number;
  riskScore: number;
  financialScore: number;
  hasTaxDebt: boolean;
}

export interface ProjectStageDto {
  id: string;
  stageOrder: number;
  name: string;
  category: string;
  quantity: number;
  unit: string;
  description: string;
  priceEstimateMin: number;
  priceEstimateMax: number;
  priceEstimateMedian: number;
  status: string;
  supplierCount: number;
  // Phase 1: Validation fields
  emtakCode?: string;
  validationConfidence?: number;
  validationIssues?: string;
  matchedSuppliersJson?: string;
  // Phase 4: Timeline fields
  plannedStartDate?: string;
  plannedDurationDays?: number;
  procurementStatus?: string;
}

// Pipeline (from Phase 2)
export interface Pipeline {
  id: string;
  projectId: string | null;
  status: 'CREATED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  currentStep: number;
  totalSteps: number;
  progressPercent: number;
  errorMessage: string | null;
  steps: PipelineStep[];
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface PipelineStep {
  id: string;
  stepOrder: number;
  stepType: string;
  stepName: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
  errorMessage: string | null;
  retryCount: number;
  startedAt: string | null;
  completedAt: string | null;
}

// Analysis (from Phase 3)
export interface ComparisonResult {
  campaignId: string;
  totalBids: number;
  bestValue: string;
  recommendation: string;
  minPrice: number;
  maxPrice: number;
  medianPrice: number;
  rankings: BidRanking[];
  riskFlags: RiskFlag[];
}

export interface BidRanking {
  supplierName: string;
  rank: number;
  score: number;
  reason: string;
}

export interface RiskFlag {
  supplierName: string;
  flag: string;
}

export interface NegotiationStrategy {
  bidId: string;
  supplierName: string;
  currentPrice: number;
  targetPrice: number;
  discountPercent: number;
  leveragePoints: string[];
  counterOfferReasoning: string;
  negotiationTone: string;
  suggestedMessage: string;
}

// Company Enrichment (from Phase 5)
export interface CompanyEnrichment {
  supplierId: string;
  companyName: string;
  llmSummary: string;
  llmSpecialties: string;
  riskScore: number;
  reliabilityScore: number;
  priceCompetitiveness: string;
  recommendedFor: string;
  tier1Complete: boolean;
  tier2Complete: boolean;
  tier3Complete: boolean;
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

export const PIPE_SYSTEM_LABELS: { [key: string]: string } = {
  'SK': 'Sademeveekanalisatsioon',
  'OK': 'Olmekanalisatsioon',
  'RV': 'Reoveekanalisatsioon',
  'VV': 'Veevarustus',
  'SV': 'Sooja vee varustus',
  'K': 'Küte',
  'DR': 'Drenaaž',
  'TV': 'Tuletõrjeveevarustus',
  'V1': 'Ventilatsioon 1',
  'V2': 'Ventilatsioon 2'
};

export const PIPE_SYSTEM_ICONS: { [key: string]: string } = {
  'SK': '🌧️',
  'OK': '🚿',
  'RV': '🚰',
  'VV': '💧',
  'SV': '🔥',
  'K': '♨️',
  'DR': '⬇️',
  'TV': '🧯',
  'V1': '💨',
  'V2': '💨'
};

export const PIPELINE_STEP_LABELS: { [key: string]: string } = {
  'PARSE_FILES': 'Failide analüüs',
  'VALIDATE_PARSE': 'Andmete valideerimine',
  'MATCH_SUPPLIERS': 'Tarnijate leidmine',
  'ENRICH_COMPANIES': 'Ettevõtete analüüs',
  'SEND_RFQS': 'Päringute saatmine',
  'AWAIT_BIDS': 'Pakkumiste ootamine',
  'COMPARE_BIDS': 'Pakkumiste võrdlemine'
};
