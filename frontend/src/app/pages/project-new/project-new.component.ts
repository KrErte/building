import { Component, signal, computed, OnInit, OnDestroy, ElementRef, ViewChild, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProjectService, UploadProgress, ProcessingStep } from '../../services/project.service';
import { RfqService } from '../../services/rfq.service';
import { CompanyService } from '../../services/company.service';
import { Company } from '../../models/company.model';
import {
  ProjectParseResult,
  ProjectStage,
  DependentMaterial,
  PipeSystem,
  PipeLengthSummary,
  CATEGORY_LABELS,
  CATEGORY_ICONS
} from '../../models/project.model';
import { RfqRequest } from '../../models/rfq.model';
import { PriceBreakdownComponent } from '../../components/price-breakdown/price-breakdown.component';
import { PipeSystemBreakdownComponent } from '../../components/pipe-system-breakdown/pipe-system-breakdown.component';
import { DependentMaterialsComponent } from '../../components/dependent-materials/dependent-materials.component';
import { PipeLengthSummaryComponent } from '../../components/pipe-length-summary/pipe-length-summary.component';
import { SummaryTableComponent } from '../../components/summary-table/summary-table.component';
import { forkJoin } from 'rxjs';

// Preview interfaces
interface PreviewCompany extends Company {
  selected: boolean;
}

interface CategoryCompanies {
  category: string;
  companies: PreviewCompany[];
  loading: boolean;
}

@Component({
  selector: 'app-project-new',
  standalone: true,
  imports: [CommonModule, FormsModule, PriceBreakdownComponent, PipeSystemBreakdownComponent, DependentMaterialsComponent, PipeLengthSummaryComponent, SummaryTableComponent],
  templateUrl: './project-new.component.html',
  styleUrls: ['./project-new.component.scss']
})
export class ProjectNewComponent implements OnInit, OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('textarea') textarea!: ElementRef<HTMLTextAreaElement>;

  description = signal('');
  selectedFiles = signal<File[]>([]);
  filePreviews = signal<Map<string, string>>(new Map());
  isLoading = signal(false);
  result = signal<ProjectParseResult | null>(null);
  error = signal<string | null>(null);
  totalSupplierCount = signal(0);
  isDragOver = signal(false);

  // Animation states
  heroTyped = signal(false);
  subtitleVisible = signal(false);
  loadingText = signal('Analüüsin projekti...');
  resultsVisible = signal(false);
  visibleCardCount = signal(0);

  // Upload progress
  uploadProgress = signal<UploadProgress | null>(null);
  isUploading = signal(false);

  // Typewriter
  typewriterText1 = signal('');
  typewriterText2 = signal('');
  showCursor1 = signal(true);
  showCursor2 = signal(false);

  // Placeholder cycling
  placeholderIndex = signal(0);
  placeholders = [
    '3-toaline korteri remont Tallinnas, vannitoa plaatimine 12m², köök 8m², elektri uuendamine...',
    'Katuse vahetus 200m², vana eterniidi eemaldamine, uus plekk-katus soojustusega...',
    'Äripinna väljaehitus 150m², vaheseinad, põrandad, laed, valgustus, ventilatsioon...',
    'Eramu fassaadi soojustamine 180m², krohvimine, akende vahetus 12 tk...',
    'Sauna ehitus keldrikorrusele, 15m², leiliruumi laudis, keris, ventilatsiooni...'
  ];
  currentPlaceholder = signal(this.placeholders[0]);

  // Loading messages (will be set dynamically based on file type)
  loadingMessages = [
    'Analüüsin projekti...',
    'Tuvastan ehitusetappe...',
    'Loen koguseid...',
    'Koostan tulemust...'
  ];
  loadingMessageIndex = signal(0);

  // File type specific loading messages (parse phase only - no pricing)
  private fileTypeMessages: Record<string, string[]> = {
    'ifc': [
      'Loen BIM mudelit...',
      'Analüüsin ehituselemente...',
      'Tuvastan seinu ja uksi...',
      'Arvutan koguseid...'
    ],
    'zip': [
      'Pakkisin ZIP-arhiivi lahti...',
      'Tuvastan failitüüpe...',
      'Analüüsin ehitusdokumente...',
      'Arvutan koguseid...'
    ],
    'dwg': [
      'Analüüsin CAD joonist...',
      'Tuvastan joonise elemente...',
      'Loen mõõtmeid...',
      'Koostan tulemust...'
    ],
    'dxf': [
      'Loen DXF joonist...',
      'Tuvastan joonise kihte...',
      'Analüüsin elemente...',
      'Arvutan koguseid...'
    ],
    'rvt': [
      'Loen Revit mudelit...',
      'Analüüsin BIM andmeid...',
      'Tuvastan ehituselemente...',
      'Arvutan koguseid...'
    ],
    'pdf': [
      'Loen PDF dokumenti...',
      'Analüüsin jooniseid...',
      'Tuvastan ehitusetappe...',
      'Arvutan koguseid...'
    ],
    'image': [
      'Analüüsin pilti AI-ga...',
      'Tuvastan ehituselemente...',
      'Loen joonise mõõtmeid...',
      'Arvutan koguseid...'
    ]
  };

  // Animated counters
  animatedTotalMin = signal(0);
  animatedTotalMax = signal(0);
  animatedSupplierCount = signal(0);

  categoryLabels = CATEGORY_LABELS;
  categoryIcons = CATEGORY_ICONS;
  isSendingRfq = signal(false);
  rfqSent = signal(false);
  rfqSentCount = signal(0);

  // Quantity editor step (after parse, before results)
  showQuantityEditor = signal(false);

  // Preview step
  showPreview = signal(false);
  previewCompanies = signal<CategoryCompanies[]>([]);
  loadingCompanies = signal(false);
  showAddCompanyModal = signal(false);
  addCompanyCategory = signal('');
  manualCompanyName = signal('');
  manualCompanyEmail = signal('');
  manualCompanyPhone = signal('');

  private intervals: number[] = [];

  selectedStages = computed(() => {
    const r = this.result();
    if (!r) return [];
    return r.stages.filter(s => s.selected);
  });

  selectedTotalMin = computed(() => {
    const stageSum = this.selectedStages().reduce((sum, s) => sum + (s.priceEstimateMin || 0), 0);
    const r = this.result();
    // Use backend's enhanced estimate if higher (includes IFC-based price calculation)
    if (r && r.totalEstimateMin && r.totalEstimateMin > stageSum) {
      return r.totalEstimateMin;
    }
    return stageSum;
  });

  selectedTotalMax = computed(() => {
    const stageSum = this.selectedStages().reduce((sum, s) => sum + (s.priceEstimateMax || 0), 0);
    const r = this.result();
    // Use backend's enhanced estimate if higher (includes IFC-based price calculation)
    if (r && r.totalEstimateMax && r.totalEstimateMax > stageSum) {
      return r.totalEstimateMax;
    }
    return stageSum;
  });

  selectedTotalMedian = computed(() => {
    return this.selectedStages().reduce((sum, s) => sum + (s.priceEstimateMedian || 0), 0);
  });

  selectedSupplierCount = computed(() => {
    return this.selectedStages().reduce((sum, s) => sum + (s.supplierCount || 0), 0);
  });

  hasPipeSystems = computed(() => {
    const r = this.result();
    return r?.pipeSystems != null && r.pipeSystems.length > 0;
  });

  // Tab system for results section
  activeTab = signal<'work' | 'materials' | 'summary'>('work');

  hasDependentMaterials = computed(() => {
    const r = this.result();
    return r?.dependentMaterials != null && r.dependentMaterials.length > 0;
  });

  dependentMaterialsCount = computed(() => {
    const r = this.result();
    return r?.dependentMaterials?.length || 0;
  });

  hasPipeLengths = computed(() => {
    const r = this.result();
    return r?.pipeLengthSummaries != null && r.pipeLengthSummaries.length > 0;
  });

  wordCount = computed(() => {
    const text = this.description();
    if (!text.trim()) return 0;
    return text.trim().split(/\s+/).length;
  });

  constructor(
    private projectService: ProjectService,
    private rfqService: RfqService,
    private companyService: CompanyService
  ) {}

  ngOnInit(): void {
    this.loadSupplierCount();
    this.startTypewriter();
    this.startPlaceholderCycle();
  }

  ngOnDestroy(): void {
    this.intervals.forEach(id => clearInterval(id));
  }

  private loadSupplierCount(): void {
    this.companyService.getCount().subscribe({
      next: (res) => this.totalSupplierCount.set(res.count),
      error: (err) => console.error('Failed to load supplier count:', err)
    });
  }

  private startTypewriter(): void {
    const text1 = 'Kirjelda projekti.';
    const text2 = 'AI leiab tegijad.';
    let i = 0;
    let j = 0;

    const type1 = setInterval(() => {
      if (i < text1.length) {
        this.typewriterText1.set(text1.substring(0, i + 1));
        i++;
      } else {
        clearInterval(type1);
        this.showCursor1.set(false);
        this.showCursor2.set(true);

        setTimeout(() => {
          const type2 = setInterval(() => {
            if (j < text2.length) {
              this.typewriterText2.set(text2.substring(0, j + 1));
              j++;
            } else {
              clearInterval(type2);
              this.heroTyped.set(true);
              setTimeout(() => this.subtitleVisible.set(true), 300);
            }
          }, 60);
          this.intervals.push(type2 as unknown as number);
        }, 400);
      }
    }, 80);
    this.intervals.push(type1 as unknown as number);
  }

  private startPlaceholderCycle(): void {
    const interval = setInterval(() => {
      const nextIndex = (this.placeholderIndex() + 1) % this.placeholders.length;
      this.placeholderIndex.set(nextIndex);
      this.currentPlaceholder.set(this.placeholders[nextIndex]);
    }, 4000);
    this.intervals.push(interval as unknown as number);
  }

  private startLoadingMessages(): void {
    const interval = setInterval(() => {
      const nextIndex = (this.loadingMessageIndex() + 1) % this.loadingMessages.length;
      this.loadingMessageIndex.set(nextIndex);
      this.loadingText.set(this.loadingMessages[nextIndex]);
    }, 1500);
    this.intervals.push(interval as unknown as number);
  }

  @HostListener('dragover', ['$event'])
  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
  }

  onDragEnter(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      for (let i = 0; i < files.length; i++) {
        this.addFile(files[i]);
      }
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      for (let i = 0; i < input.files.length; i++) {
        this.addFile(input.files[i]);
      }
    }
  }

  private addFile(file: File): void {
    // Supported file extensions (including ZIP for compressed IFC)
    const supportedExtensions = /\.(pdf|docx?|txt|jpe?g|png|gif|bmp|webp|ifc|dwg|dxf|rvt|zip)$/i;

    if (!supportedExtensions.test(file.name)) {
      this.error.set('Lubatud: PDF, DOCX, TXT, IFC, DWG, DXF, RVT, ZIP, JPG, PNG');
      return;
    }

    // Check file size (max 7GB for ZIP, 2GB for BIM files, 500MB for others)
    const isZip = file.name.toLowerCase().endsWith('.zip');
    const maxSize = isZip ? 7 * 1024 * 1024 * 1024 : (this.isBimFile(file.name) ? 2 * 1024 * 1024 * 1024 : 500 * 1024 * 1024);
    if (file.size > maxSize) {
      const maxMB = maxSize / (1024 * 1024);
      this.error.set(`Fail on liiga suur (max ${maxMB}MB)`);
      return;
    }

    // Check for duplicate filename
    const existing = this.selectedFiles();
    if (existing.some(f => f.name === file.name && f.size === file.size)) {
      return; // Skip duplicate
    }

    this.selectedFiles.set([...existing, file]);
    this.error.set(null);

    // Set loading messages based on first file type
    if (existing.length === 0) {
      this.setLoadingMessagesForFile(file.name);
    }

    // Generate preview for images
    if (file.type.startsWith('image/') || this.isImageFile(file.name)) {
      const reader = new FileReader();
      reader.onload = (e) => {
        const previews = new Map(this.filePreviews());
        previews.set(file.name, e.target?.result as string);
        this.filePreviews.set(previews);
      };
      reader.readAsDataURL(file);
    }
  }

  private isBimFile(filename: string): boolean {
    return /\.(ifc|rvt|dwg|dxf|zip)$/i.test(filename);
  }

  private isImageFile(filename: string): boolean {
    return /\.(jpe?g|png|gif|bmp|webp)$/i.test(filename);
  }

  private setLoadingMessagesForFile(filename: string): void {
    const lower = filename.toLowerCase();
    let key = 'default';

    if (lower.endsWith('.ifc')) key = 'ifc';
    else if (lower.endsWith('.zip')) key = 'zip';
    else if (lower.endsWith('.dwg')) key = 'dwg';
    else if (lower.endsWith('.dxf')) key = 'dxf';
    else if (lower.endsWith('.rvt')) key = 'rvt';
    else if (lower.endsWith('.pdf')) key = 'pdf';
    else if (this.isImageFile(lower)) key = 'image';

    if (this.fileTypeMessages[key]) {
      this.loadingMessages = this.fileTypeMessages[key];
    }
  }

  getFileIcon(filename: string | undefined): string {
    if (!filename) return '📎';
    const lower = filename.toLowerCase();

    if (lower.endsWith('.pdf')) return '📄';
    if (lower.endsWith('.docx') || lower.endsWith('.doc')) return '📝';
    if (lower.endsWith('.txt')) return '📃';
    if (lower.endsWith('.ifc')) return '🏗️';
    if (lower.endsWith('.zip')) return '📦';
    if (lower.endsWith('.dwg')) return '📐';
    if (lower.endsWith('.dxf')) return '📏';
    if (lower.endsWith('.rvt')) return '🏛️';
    if (this.isImageFile(lower)) return '🖼️';

    return '📎';
  }

  getFileTypeLabel(filename: string | undefined): string {
    if (!filename) return 'Fail';
    const lower = filename.toLowerCase();

    if (lower.endsWith('.pdf')) return 'PDF';
    if (lower.endsWith('.docx')) return 'Word';
    if (lower.endsWith('.doc')) return 'Word';
    if (lower.endsWith('.txt')) return 'Tekst';
    if (lower.endsWith('.ifc')) return 'BIM/IFC';
    if (lower.endsWith('.zip')) return 'ZIP-arhiiv';
    if (lower.endsWith('.dwg')) return 'AutoCAD';
    if (lower.endsWith('.dxf')) return 'CAD/DXF';
    if (lower.endsWith('.rvt')) return 'Revit';
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'JPEG';
    if (lower.endsWith('.png')) return 'PNG';

    return 'Fail';
  }

  removeFile(file?: File): void {
    if (file) {
      // Remove specific file
      this.selectedFiles.set(this.selectedFiles().filter(f => f !== file));
      const previews = new Map(this.filePreviews());
      previews.delete(file.name);
      this.filePreviews.set(previews);
    } else {
      // Remove all
      this.selectedFiles.set([]);
      this.filePreviews.set(new Map());
    }
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  triggerFileInput(): void {
    this.fileInput.nativeElement.click();
  }

  getTotalFileSize(): number {
    return this.selectedFiles().reduce((sum, f) => sum + f.size, 0);
  }

  getFilePreview(file: File): string | null {
    return this.filePreviews().get(file.name) || null;
  }

  parseProject(): void {
    this.error.set(null);
    this.isLoading.set(true);
    this.uploadProgress.set(null);
    this.loadingMessageIndex.set(0);
    this.loadingText.set(this.loadingMessages[0]);

    const files = this.selectedFiles();
    if (files.length > 0) {
      // Use progress tracking for file uploads
      this.isUploading.set(true);
      this.projectService.parseFromFilesWithProgress(
        files,
        (progress) => {
          this.uploadProgress.set(progress);
          if (progress.phase === 'processing' && progress.processingStep) {
            this.loadingText.set(progress.processingStep);
          }
        }
      ).subscribe({
        next: (res) => {
          this.isUploading.set(false);
          this.handleResult(res);
        },
        error: (err) => {
          this.isUploading.set(false);
          this.handleError(err);
        }
      });
    } else {
      const desc = this.description();
      if (!desc.trim()) {
        this.error.set('Palun sisesta projekti kirjeldus või lohista fail');
        this.isLoading.set(false);
        return;
      }
      this.startLoadingMessages();
      this.projectService.parseFromText(desc).subscribe({
        next: (res) => this.handleResult(res),
        error: (err) => this.handleError(err)
      });
    }
  }

  formatSpeed(bytesPerSecond: number): string {
    if (bytesPerSecond < 1024) return bytesPerSecond.toFixed(0) + ' B/s';
    if (bytesPerSecond < 1024 * 1024) return (bytesPerSecond / 1024).toFixed(1) + ' KB/s';
    return (bytesPerSecond / (1024 * 1024)).toFixed(1) + ' MB/s';
  }

  formatTime(seconds: number): string {
    if (seconds < 60) return Math.round(seconds) + ' sek';
    const mins = Math.floor(seconds / 60);
    const secs = Math.round(seconds % 60);
    return `${mins} min ${secs} sek`;
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
  }

  private handleResult(res: ProjectParseResult): void {
    res.stages = res.stages.map(s => ({
      ...s,
      selected: true,
      expanded: false
    }));

    // Initialize pipe system expanded state
    if (res.pipeSystems) {
      res.pipeSystems = res.pipeSystems.map(ps => ({
        ...ps,
        expanded: false
      }));
    }

    this.result.set(res);
    this.isLoading.set(false);
    this.showQuantityEditor.set(true);
  }

  confirmQuantities(): void {
    const r = this.result();
    if (!r) return;

    this.isLoading.set(true);
    this.loadingMessages = [
      'Arvutan turuhinda...',
      'Otsin sobivaid tegijaid...',
      'Võrdlen pakkumisi...',
      'Koostan hinnakalkulatsiooni...'
    ];
    this.loadingMessageIndex.set(0);
    this.loadingText.set(this.loadingMessages[0]);
    this.startLoadingMessages();

    // Send confirmed quantities to backend for price estimation
    this.projectService.estimatePrices(r).subscribe({
      next: (enriched) => {
        enriched.stages = enriched.stages.map(s => ({
          ...s,
          selected: true,
          expanded: false
        }));
        this.result.set(enriched);
        this.isLoading.set(false);
        this.showQuantityEditor.set(false);
        this.resultsVisible.set(true);

        // Stagger card animations
        this.visibleCardCount.set(0);
        enriched.stages.forEach((_, index) => {
          setTimeout(() => {
            this.visibleCardCount.set(index + 1);
          }, 150 * (index + 1));
        });

        setTimeout(() => {
          this.animateCounters();
        }, enriched.stages.length * 150 + 300);
      },
      error: (err) => {
        console.error('Price estimation error:', err);
        this.error.set('Hindade arvutamine ebaõnnestus. Palun proovi uuesti.');
        this.isLoading.set(false);
      }
    });
  }

  backToQuantityEditor(): void {
    this.showQuantityEditor.set(true);
    this.resultsVisible.set(false);
    this.visibleCardCount.set(0);
  }

  private animateCounters(): void {
    const targetMin = this.selectedTotalMin();
    const targetMax = this.selectedTotalMax();
    const targetSuppliers = this.selectedSupplierCount();
    const duration = 1500;
    const steps = 60;
    const stepTime = duration / steps;

    let step = 0;
    const interval = setInterval(() => {
      step++;
      const progress = this.easeOutQuart(step / steps);
      this.animatedTotalMin.set(Math.round(targetMin * progress));
      this.animatedTotalMax.set(Math.round(targetMax * progress));
      this.animatedSupplierCount.set(Math.round(targetSuppliers * progress));

      if (step >= steps) {
        clearInterval(interval);
        this.animatedTotalMin.set(targetMin);
        this.animatedTotalMax.set(targetMax);
        this.animatedSupplierCount.set(targetSuppliers);
      }
    }, stepTime);
    this.intervals.push(interval as unknown as number);
  }

  private easeOutQuart(x: number): number {
    return 1 - Math.pow(1 - x, 4);
  }

  private handleError(err: unknown): void {
    console.error('Parse error:', err);
    this.error.set('Projekti analüüsimine ebaõnnestus. Palun proovi uuesti.');
    this.isLoading.set(false);
  }

  toggleStage(stage: ProjectStage): void {
    stage.expanded = !stage.expanded;
  }

  toggleSelection(stage: ProjectStage, event: Event): void {
    event.stopPropagation();
    stage.selected = !stage.selected;
    // Re-animate counters
    this.animateCounters();
  }

  selectAll(): void {
    const r = this.result();
    if (r) {
      r.stages.forEach(s => s.selected = true);
      this.animateCounters();
    }
  }

  deselectAll(): void {
    const r = this.result();
    if (r) {
      r.stages.forEach(s => s.selected = false);
      this.animatedTotalMin.set(0);
      this.animatedTotalMax.set(0);
      this.animatedSupplierCount.set(0);
    }
  }

  sendRfq(): void {
    const selected = this.selectedStages();
    if (selected.length === 0) {
      this.error.set('Palun vali vähemalt üks etapp');
      return;
    }

    this.error.set(null);
    this.isSendingRfq.set(true);

    const result = this.result();
    if (!result) return;

    let sentCount = 0;
    let completed = 0;

    selected.forEach(stage => {
      const request: RfqRequest = {
        title: `${stage.name} - ${result.projectTitle}`,
        category: stage.category,
        location: result.location,
        quantity: stage.quantity,
        unit: stage.unit,
        specifications: stage.description,
        maxBudget: stage.priceEstimateMax,
        deadline: null,
        supplierIds: []
      };

      this.rfqService.sendRfq(request).subscribe({
        next: (campaign) => {
          sentCount += campaign.totalSent;
          completed++;
          if (completed === selected.length) {
            this.isSendingRfq.set(false);
            this.rfqSent.set(true);
            this.rfqSentCount.set(sentCount);
          }
        },
        error: (err) => {
          console.error('Error sending RFQ:', err);
          completed++;
          if (completed === selected.length) {
            this.isSendingRfq.set(false);
            if (sentCount > 0) {
              this.rfqSent.set(true);
              this.rfqSentCount.set(sentCount);
            } else {
              this.error.set('Hinnapäringute saatmine ebaõnnestus');
            }
          }
        }
      });
    });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('et-EE', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value);
  }

  getCategoryLabel(category: string): string {
    return this.categoryLabels[category] || category;
  }

  getCategoryIcon(category: string): string {
    return this.categoryIcons[category] || '📦';
  }

  getPriceLevel(stage: ProjectStage): string {
    const median = stage.priceEstimateMedian || 0;
    const max = stage.priceEstimateMax || 1;
    const ratio = median / max;
    if (ratio < 0.4) return 'low';
    if (ratio < 0.7) return 'medium';
    return 'high';
  }

  getMedianPosition(stage: ProjectStage): number {
    const min = stage.priceEstimateMin || 0;
    const max = stage.priceEstimateMax || 1;
    const median = stage.priceEstimateMedian || 0;
    return ((median - min) / (max - min)) * 100;
  }

  setTab(tab: 'work' | 'materials' | 'summary'): void {
    this.activeTab.set(tab);
  }

  resetForm(): void {
    this.description.set('');
    this.selectedFiles.set([]);
    this.filePreviews.set(new Map());
    this.result.set(null);
    this.error.set(null);
    this.rfqSent.set(false);
    this.rfqSentCount.set(0);
    this.resultsVisible.set(false);
    this.visibleCardCount.set(0);
    this.animatedTotalMin.set(0);
    this.animatedTotalMax.set(0);
    this.animatedSupplierCount.set(0);
    this.showPreview.set(false);
    this.previewCompanies.set([]);
    this.showQuantityEditor.set(false);
  }

  // Preview step methods
  openPreview(): void {
    const selected = this.selectedStages();
    if (selected.length === 0) {
      this.error.set('Palun vali vähemalt üks etapp');
      return;
    }

    this.showPreview.set(true);
    this.loadCompaniesForCategories();
  }

  closePreview(): void {
    this.showPreview.set(false);
  }

  private loadCompaniesForCategories(): void {
    const selected = this.selectedStages();
    const categories = [...new Set(selected.map(s => s.category))];
    const result = this.result();
    const location = result?.location || '';

    this.loadingCompanies.set(true);

    // Initialize with loading state
    this.previewCompanies.set(categories.map(cat => ({
      category: cat,
      companies: [],
      loading: true
    })));

    // Fetch companies for each category
    const requests = categories.map(cat =>
      this.companyService.getCompaniesByCategory(cat, location, 10)
    );

    forkJoin(requests).subscribe({
      next: (responses) => {
        const categoriesWithCompanies = categories.map((cat, idx) => ({
          category: cat,
          companies: responses[idx].companies.map(c => ({ ...c, selected: true })),
          loading: false
        }));
        this.previewCompanies.set(categoriesWithCompanies);
        this.loadingCompanies.set(false);
      },
      error: (err) => {
        console.error('Error loading companies:', err);
        this.loadingCompanies.set(false);
      }
    });
  }

  updateQuantity(stage: ProjectStage, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = parseFloat(input.value);
    if (!isNaN(value) && value > 0) {
      stage.quantity = value;
    }
  }

  toggleCompanySelection(categoryIndex: number, companyIndex: number): void {
    const categories = this.previewCompanies();
    const company = categories[categoryIndex]?.companies[companyIndex];
    if (company) {
      company.selected = !company.selected;
      this.previewCompanies.set([...categories]);
    }
  }

  selectAllCompanies(categoryIndex: number): void {
    const categories = this.previewCompanies();
    categories[categoryIndex]?.companies.forEach(c => c.selected = true);
    this.previewCompanies.set([...categories]);
  }

  deselectAllCompanies(categoryIndex: number): void {
    const categories = this.previewCompanies();
    categories[categoryIndex]?.companies.forEach(c => c.selected = false);
    this.previewCompanies.set([...categories]);
  }

  openAddCompanyModal(category: string): void {
    this.addCompanyCategory.set(category);
    this.manualCompanyName.set('');
    this.manualCompanyEmail.set('');
    this.manualCompanyPhone.set('');
    this.showAddCompanyModal.set(true);
  }

  closeAddCompanyModal(): void {
    this.showAddCompanyModal.set(false);
  }

  addManualCompany(): void {
    const name = this.manualCompanyName();
    const email = this.manualCompanyEmail();
    const phone = this.manualCompanyPhone();
    const category = this.addCompanyCategory();

    if (!name.trim()) {
      return;
    }

    const newCompany: PreviewCompany = {
      id: 'manual-' + Date.now(),
      companyName: name,
      contactPerson: null,
      email: email || null,
      phone: phone || null,
      website: null,
      address: null,
      city: null,
      county: null,
      categories: [category],
      serviceAreas: [],
      source: 'MANUAL',
      googleRating: null,
      googleReviewCount: null,
      trustScore: null,
      isVerified: false,
      selected: true
    };

    const categories = this.previewCompanies();
    const catIndex = categories.findIndex(c => c.category === category);
    if (catIndex >= 0) {
      categories[catIndex].companies.push(newCompany);
      this.previewCompanies.set([...categories]);
    }

    this.closeAddCompanyModal();
  }

  getSelectedCompanyCount(categoryIndex: number): number {
    const categories = this.previewCompanies();
    return categories[categoryIndex]?.companies.filter(c => c.selected).length || 0;
  }

  getTotalSelectedCompanyCount(): number {
    return this.previewCompanies().reduce(
      (sum, cat) => sum + cat.companies.filter(c => c.selected).length,
      0
    );
  }

  confirmAndSendRfq(): void {
    const selected = this.selectedStages();
    if (selected.length === 0) {
      this.error.set('Palun vali vähemalt üks etapp');
      return;
    }

    const totalCompanies = this.getTotalSelectedCompanyCount();
    if (totalCompanies === 0) {
      this.error.set('Palun vali vähemalt üks ettevõte');
      return;
    }

    this.error.set(null);
    this.isSendingRfq.set(true);
    this.showPreview.set(false);

    const result = this.result();
    if (!result) return;

    let sentCount = 0;
    let completed = 0;
    const categories = this.previewCompanies();

    selected.forEach(stage => {
      const catCompanies = categories.find(c => c.category === stage.category);
      const selectedCompanyIds = catCompanies?.companies
        .filter(c => c.selected)
        .map(c => c.id) || [];

      const request: RfqRequest = {
        title: `${stage.name} - ${result.projectTitle}`,
        category: stage.category,
        location: result.location,
        quantity: stage.quantity,
        unit: stage.unit,
        specifications: stage.description,
        maxBudget: stage.priceEstimateMax,
        deadline: null,
        supplierIds: selectedCompanyIds
      };

      this.rfqService.sendRfq(request).subscribe({
        next: (campaign) => {
          sentCount += selectedCompanyIds.length;
          completed++;
          if (completed === selected.length) {
            this.isSendingRfq.set(false);
            this.rfqSent.set(true);
            this.rfqSentCount.set(sentCount);
          }
        },
        error: (err) => {
          console.error('Error sending RFQ:', err);
          completed++;
          if (completed === selected.length) {
            this.isSendingRfq.set(false);
            if (sentCount > 0) {
              this.rfqSent.set(true);
              this.rfqSentCount.set(sentCount);
            } else {
              this.error.set('Hinnapäringute saatmine ebaõnnestus');
            }
          }
        }
      });
    });
  }
}
