import {
  Component,
  Directive,
  ElementRef,
  OnInit,
  inject,
  signal,
  computed,
  DestroyRef,
  afterNextRender,
  input,
  output,
  ViewEncapsulation,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  trigger,
  state,
  style,
  animate,
  transition,
  query,
  stagger,
  keyframes,
  AnimationEvent,
} from '@angular/animations';

// ============================================
// SCROLL TRIGGER DIRECTIVE
// ============================================
@Directive({
  selector: '[appScrollTrigger]',
  standalone: true,
})
export class ScrollTriggerDirective implements OnInit {
  private el = inject(ElementRef);
  private destroyRef = inject(DestroyRef);

  threshold = input<number>(0.3);
  triggered = output<boolean>();

  private observer: IntersectionObserver | null = null;
  isVisible = signal(false);

  ngOnInit() {
    this.observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && !this.isVisible()) {
            this.isVisible.set(true);
            this.triggered.emit(true);
          }
        });
      },
      {
        threshold: this.threshold(),
        rootMargin: '0px 0px -50px 0px',
      }
    );

    this.observer.observe(this.el.nativeElement);

    this.destroyRef.onDestroy(() => {
      this.observer?.disconnect();
    });
  }
}

// ============================================
// HOW IT WORKS COMPONENT
// ============================================
@Component({
  selector: 'app-how-it-works',
  standalone: true,
  imports: [CommonModule, ScrollTriggerDirective],
  template: `
    <section
      class="how-it-works"
      appScrollTrigger
      [threshold]="0.2"
      (triggered)="onSectionVisible()"
    >
      <!-- Background Effects -->
      <div class="bg-effects">
        <div class="gradient-orb orb-1"></div>
        <div class="gradient-orb orb-2"></div>
        <div class="grid-overlay"></div>
      </div>

      <!-- Section Header -->
      <header class="section-header" [class.visible]="sectionVisible()">
        <h2 class="main-title">
          Kuidas <span class="gradient-text">BuildQuote</span> töötab?
        </h2>
        <p class="subtitle">Kolm lihtsat sammu parima pakkumiseni</p>
      </header>

      <!-- Timeline Progress Bar -->
      <div class="timeline-progress" [class.visible]="sectionVisible()">
        <div
          class="progress-fill"
          [style.width.%]="timelineProgress()"
        ></div>
      </div>

      <!-- Steps Container -->
      <div class="steps-container" [class.visible]="sectionVisible()">
        <!-- Connector Lines SVG -->
        <svg class="connector-lines" viewBox="0 0 1000 100" preserveAspectRatio="none">
          <defs>
            <linearGradient id="lineGradient" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stop-color="#7c3aed" />
              <stop offset="50%" stop-color="#a855f7" />
              <stop offset="100%" stop-color="#7c3aed" />
            </linearGradient>
          </defs>
          <path
            class="connector-path path-1"
            d="M 170 50 Q 250 50 333 50"
            fill="none"
            stroke="url(#lineGradient)"
            stroke-width="2"
            stroke-dasharray="8 4"
            [style.strokeDashoffset]="connector1Offset()"
          />
          <path
            class="connector-path path-2"
            d="M 500 50 Q 583 50 666 50"
            fill="none"
            stroke="url(#lineGradient)"
            stroke-width="2"
            stroke-dasharray="8 4"
            [style.strokeDashoffset]="connector2Offset()"
          />
        </svg>

        <!-- Step 1: Sisesta töö -->
        <article
          class="step-card"
          [class.active]="currentStep() >= 1"
          [class.completed]="currentStep() > 1"
          (click)="replayFromStep(1)"
          [@stepAnimation]="currentStep() >= 1 ? 'active' : 'inactive'"
        >
          <div class="step-number">
            <span class="number-ring">
              <span class="number">1</span>
            </span>
          </div>

          <div class="step-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M13 3v5a2 2 0 002 2h5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>

          <h3 class="step-title">Sisesta töö</h3>

          <div class="step-mockup glass-card">
            <div class="mockup-header">
              <div class="mockup-dots">
                <span></span><span></span><span></span>
              </div>
            </div>
            <div class="mockup-content form-mockup">
              <div class="form-field">
                <label>Kirjeldus</label>
                <div class="input-field typing-field">
                  <span class="typed-text">{{ typedDescription() }}</span>
                  <span class="cursor" [class.blink]="currentStep() === 1"></span>
                </div>
              </div>
              <div class="form-field" [class.visible]="formFieldsVisible() >= 1">
                <label>Kategooria</label>
                <div class="select-field">
                  <span>Katused & katusetööd</span>
                  <svg viewBox="0 0 20 20" fill="currentColor">
                    <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"/>
                  </svg>
                </div>
              </div>
              <div class="form-field" [class.visible]="formFieldsVisible() >= 2">
                <label>Asukoht</label>
                <div class="input-field">Tallinn</div>
              </div>
              <button class="submit-btn" [class.pulse]="currentStep() === 1 && formFieldsVisible() >= 2">
                <span>Leia tarnijad</span>
                <svg viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M10.293 3.293a1 1 0 011.414 0l6 6a1 1 0 010 1.414l-6 6a1 1 0 01-1.414-1.414L14.586 11H3a1 1 0 110-2h11.586l-4.293-4.293a1 1 0 010-1.414z"/>
                </svg>
              </button>
            </div>
          </div>

          <div class="step-tooltip">
            <p>Kirjelda oma ehitustööd ja BuildQuote analüüsib selle automaatselt</p>
          </div>
        </article>

        <!-- Step 2: AI leiab tarnijad -->
        <article
          class="step-card"
          [class.active]="currentStep() >= 2"
          [class.completed]="currentStep() > 2"
          (click)="replayFromStep(2)"
          [@stepAnimation]="currentStep() >= 2 ? 'active' : 'inactive'"
        >
          <div class="step-number">
            <span class="number-ring">
              <span class="number">2</span>
            </span>
          </div>

          <div class="step-icon sparkle">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M13 10V3L4 14h7v7l9-11h-7z" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>

          <h3 class="step-title">AI leiab tarnijad</h3>

          <div class="step-mockup glass-card ai-mockup">
            <div class="ai-visualization">
              <!-- Central AI Node -->
              <div class="ai-brain" [class.active]="currentStep() >= 2">
                <div class="brain-core">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
                <div class="brain-pulse"></div>
                <div class="brain-pulse delay-1"></div>
                <div class="brain-pulse delay-2"></div>
              </div>

              <!-- Supplier Nodes -->
              @for (supplier of suppliers(); track supplier.id; let i = $index) {
                <div
                  class="supplier-node"
                  [class.visible]="supplierNodesVisible() > i"
                  [style.--angle]="supplier.angle + 'deg'"
                  [style.--delay]="i * 0.15 + 's'"
                >
                  <div class="connection-line">
                    <svg viewBox="0 0 100 2" preserveAspectRatio="none">
                      <line
                        x1="0" y1="1" x2="100" y2="1"
                        stroke="url(#lineGradient)"
                        stroke-width="2"
                        stroke-dasharray="4 2"
                        [class.animated]="supplierNodesVisible() > i"
                      />
                    </svg>
                    <div class="data-particle"></div>
                  </div>
                  <div class="node-content">
                    <div class="supplier-avatar">{{ supplier.initial }}</div>
                    <div class="match-score" [class.visible]="supplierScoresVisible() > i">
                      {{ supplier.score }}%
                    </div>
                  </div>
                </div>
              }
            </div>
          </div>

          <div class="step-tooltip">
            <p>Meie AI analüüsib 500+ tarnijat ja leiab parimad sobivused</p>
          </div>
        </article>

        <!-- Step 3: Vali parim pakkumine -->
        <article
          class="step-card"
          [class.active]="currentStep() >= 3"
          (click)="replayFromStep(3)"
          [@stepAnimation]="currentStep() >= 3 ? 'active' : 'inactive'"
        >
          <div class="step-number">
            <span class="number-ring">
              <span class="number">3</span>
            </span>
          </div>

          <div class="step-icon trophy">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>

          <h3 class="step-title">Vali parim pakkumine</h3>

          <div class="step-mockup glass-card offers-mockup">
            <div class="offers-list">
              @for (offer of offers(); track offer.id; let i = $index) {
                <div
                  class="offer-card"
                  [class.visible]="offersVisible() > i"
                  [class.best]="offer.isBest"
                  [style.--delay]="i * 0.2 + 's'"
                >
                  @if (offer.isBest) {
                    <div class="best-badge">
                      <svg viewBox="0 0 20 20" fill="currentColor">
                        <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/>
                      </svg>
                      Parim
                    </div>
                  }
                  <div class="offer-supplier">
                    <div class="supplier-logo">{{ offer.initial }}</div>
                    <div class="supplier-info">
                      <span class="name">{{ offer.name }}</span>
                      <div class="rating">
                        @for (star of [1,2,3,4,5]; track star) {
                          <svg
                            viewBox="0 0 20 20"
                            [class.filled]="star <= offer.rating"
                          >
                            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/>
                          </svg>
                        }
                      </div>
                    </div>
                  </div>
                  <div class="offer-price">{{ offer.price }}</div>
                  <button class="select-btn" [class.golden-pulse]="offer.isBest && currentStep() === 3">
                    Vali
                  </button>
                </div>
              }
            </div>
          </div>

          <div class="step-tooltip">
            <p>Võrdle pakkumisi ja vali oma projektile parim partner</p>
          </div>
        </article>
      </div>

      <!-- Replay Button -->
      <button class="replay-btn" [class.visible]="animationComplete()" (click)="replayAnimation()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>Vaata uuesti</span>
      </button>
    </section>
  `,
  styles: [`
    @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600&family=Outfit:wght@600;700;800&display=swap');

    :host {
      display: block;
      --primary: #7c3aed;
      --primary-light: #a855f7;
      --accent: #f59e0b;
      --accent-light: #fbbf24;
      --bg-dark: #0a0a1a;
      --bg-medium: #111127;
      --bg-card: rgba(255, 255, 255, 0.03);
      --border-subtle: rgba(255, 255, 255, 0.08);
      --text-primary: #ffffff;
      --text-secondary: rgba(255, 255, 255, 0.7);
      --text-muted: rgba(255, 255, 255, 0.5);
    }

    .how-it-works {
      position: relative;
      min-height: 100vh;
      padding: 120px 40px;
      background: linear-gradient(180deg, var(--bg-dark) 0%, var(--bg-medium) 100%);
      overflow: hidden;
      font-family: 'DM Sans', sans-serif;
    }

    /* Background Effects */
    .bg-effects {
      position: absolute;
      inset: 0;
      pointer-events: none;
      overflow: hidden;
    }

    .gradient-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(100px);
      opacity: 0.3;
    }

    .orb-1 {
      width: 600px;
      height: 600px;
      background: radial-gradient(circle, var(--primary) 0%, transparent 70%);
      top: -200px;
      left: -200px;
      animation: float 20s ease-in-out infinite;
    }

    .orb-2 {
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, var(--primary-light) 0%, transparent 70%);
      bottom: -150px;
      right: -150px;
      animation: float 25s ease-in-out infinite reverse;
    }

    .grid-overlay {
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px);
      background-size: 60px 60px;
      mask-image: radial-gradient(ellipse 80% 50% at 50% 50%, black 40%, transparent 100%);
    }

    @keyframes float {
      0%, 100% { transform: translate(0, 0) scale(1); }
      33% { transform: translate(30px, -30px) scale(1.05); }
      66% { transform: translate(-20px, 20px) scale(0.95); }
    }

    /* Section Header */
    .section-header {
      text-align: center;
      margin-bottom: 80px;
      opacity: 0;
      transform: translateY(40px);
      transition: all 0.8s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .section-header.visible {
      opacity: 1;
      transform: translateY(0);
    }

    .main-title {
      font-family: 'Outfit', sans-serif;
      font-size: clamp(2rem, 5vw, 3.5rem);
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 16px;
      letter-spacing: -0.02em;
    }

    .gradient-text {
      background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 50%, var(--accent) 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .subtitle {
      font-size: clamp(1rem, 2vw, 1.25rem);
      color: var(--text-secondary);
      margin: 0;
      font-weight: 500;
    }

    /* Timeline Progress */
    .timeline-progress {
      position: relative;
      max-width: 800px;
      height: 4px;
      margin: 0 auto 60px;
      background: var(--border-subtle);
      border-radius: 2px;
      opacity: 0;
      transform: scaleX(0.8);
      transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.3s;
    }

    .timeline-progress.visible {
      opacity: 1;
      transform: scaleX(1);
    }

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--primary) 0%, var(--primary-light) 100%);
      border-radius: 2px;
      transition: width 0.5s cubic-bezier(0.16, 1, 0.3, 1);
      box-shadow: 0 0 20px var(--primary);
    }

    /* Steps Container */
    .steps-container {
      position: relative;
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 40px;
      max-width: 1400px;
      margin: 0 auto;
      opacity: 0;
      transform: translateY(30px);
      transition: all 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.5s;
    }

    .steps-container.visible {
      opacity: 1;
      transform: translateY(0);
    }

    /* Connector Lines */
    .connector-lines {
      position: absolute;
      top: 60px;
      left: 0;
      right: 0;
      height: 100px;
      pointer-events: none;
      z-index: 1;
    }

    .connector-path {
      transition: stroke-dashoffset 1s cubic-bezier(0.16, 1, 0.3, 1);
    }

    /* Step Card */
    .step-card {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 40px 24px;
      cursor: pointer;
      z-index: 2;
      transition: transform 0.3s ease;
    }

    .step-card:hover {
      transform: translateY(-8px);
    }

    .step-card::before {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, var(--bg-card) 0%, transparent 100%);
      border-radius: 24px;
      border: 1px solid var(--border-subtle);
      opacity: 0;
      transition: opacity 0.3s ease;
    }

    .step-card:hover::before,
    .step-card.active::before {
      opacity: 1;
    }

    /* Step Number */
    .step-number {
      margin-bottom: 24px;
    }

    .number-ring {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: conic-gradient(from 0deg, var(--primary), var(--primary-light), var(--accent), var(--primary));
      padding: 3px;
      opacity: 0.5;
      transition: all 0.5s ease;
    }

    .step-card.active .number-ring {
      opacity: 1;
      box-shadow: 0 0 30px rgba(124, 58, 237, 0.5);
    }

    .number-ring .number {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      background: var(--bg-dark);
      border-radius: 50%;
      font-family: 'Outfit', sans-serif;
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--text-primary);
    }

    /* Step Icon */
    .step-icon {
      width: 48px;
      height: 48px;
      margin-bottom: 20px;
      color: var(--text-muted);
      transition: all 0.5s ease;
    }

    .step-card.active .step-icon {
      color: var(--primary-light);
      filter: drop-shadow(0 0 10px var(--primary));
    }

    .step-icon.sparkle {
      animation: none;
    }

    .step-card.active .step-icon.sparkle {
      animation: sparkle 2s ease-in-out infinite;
    }

    .step-icon.trophy {
      color: var(--text-muted);
    }

    .step-card.active .step-icon.trophy {
      color: var(--accent);
      filter: drop-shadow(0 0 10px var(--accent));
    }

    @keyframes sparkle {
      0%, 100% { transform: scale(1) rotate(0deg); }
      25% { transform: scale(1.1) rotate(-5deg); }
      75% { transform: scale(1.1) rotate(5deg); }
    }

    /* Step Title */
    .step-title {
      font-family: 'Outfit', sans-serif;
      font-size: 1.35rem;
      font-weight: 700;
      color: var(--text-primary);
      margin: 0 0 24px;
      text-align: center;
    }

    /* Glass Card / Mockup */
    .glass-card {
      width: 100%;
      max-width: 280px;
      background: rgba(255, 255, 255, 0.03);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid var(--border-subtle);
      border-radius: 16px;
      overflow: hidden;
      transition: all 0.3s ease;
    }

    .step-card:hover .glass-card {
      border-color: rgba(124, 58, 237, 0.3);
      box-shadow: 0 8px 32px rgba(124, 58, 237, 0.15);
    }

    .mockup-header {
      padding: 12px 16px;
      border-bottom: 1px solid var(--border-subtle);
      background: rgba(0, 0, 0, 0.2);
    }

    .mockup-dots {
      display: flex;
      gap: 6px;
    }

    .mockup-dots span {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--text-muted);
    }

    .mockup-dots span:first-child { background: #ff5f56; }
    .mockup-dots span:nth-child(2) { background: #ffbd2e; }
    .mockup-dots span:last-child { background: #27ca40; }

    .mockup-content {
      padding: 20px;
    }

    /* Form Mockup (Step 1) */
    .form-mockup .form-field {
      margin-bottom: 16px;
      opacity: 0;
      transform: translateY(10px);
      transition: all 0.4s ease;
    }

    .form-mockup .form-field:first-child {
      opacity: 1;
      transform: translateY(0);
    }

    .form-mockup .form-field.visible {
      opacity: 1;
      transform: translateY(0);
    }

    .form-mockup label {
      display: block;
      font-size: 0.7rem;
      color: var(--text-muted);
      margin-bottom: 6px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .input-field,
    .select-field {
      padding: 10px 12px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border-subtle);
      border-radius: 8px;
      font-size: 0.85rem;
      color: var(--text-primary);
    }

    .typing-field {
      min-height: 40px;
      display: flex;
      align-items: center;
    }

    .typed-text {
      color: var(--text-primary);
    }

    .cursor {
      display: inline-block;
      width: 2px;
      height: 1em;
      background: var(--primary-light);
      margin-left: 2px;
    }

    .cursor.blink {
      animation: blink 0.8s step-end infinite;
    }

    @keyframes blink {
      50% { opacity: 0; }
    }

    .select-field {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .select-field svg {
      width: 16px;
      height: 16px;
      color: var(--text-muted);
    }

    .submit-btn {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 12px;
      background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
      border: none;
      border-radius: 8px;
      font-family: 'DM Sans', sans-serif;
      font-size: 0.9rem;
      font-weight: 600;
      color: white;
      cursor: pointer;
      transition: all 0.3s ease;
    }

    .submit-btn:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(124, 58, 237, 0.4);
    }

    .submit-btn.pulse {
      animation: pulse-btn 2s ease-in-out infinite;
    }

    @keyframes pulse-btn {
      0%, 100% { box-shadow: 0 0 0 0 rgba(124, 58, 237, 0.4); }
      50% { box-shadow: 0 0 0 12px rgba(124, 58, 237, 0); }
    }

    .submit-btn svg {
      width: 16px;
      height: 16px;
    }

    /* AI Mockup (Step 2) */
    .ai-mockup {
      padding: 24px;
      min-height: 200px;
    }

    .ai-visualization {
      position: relative;
      width: 100%;
      height: 180px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .ai-brain {
      position: relative;
      width: 60px;
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 2;
    }

    .brain-core {
      width: 50px;
      height: 50px;
      background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.5s ease;
    }

    .ai-brain.active .brain-core {
      box-shadow: 0 0 30px var(--primary);
    }

    .brain-core svg {
      width: 28px;
      height: 28px;
      color: white;
    }

    .brain-pulse {
      position: absolute;
      inset: 0;
      border: 2px solid var(--primary);
      border-radius: 50%;
      opacity: 0;
    }

    .ai-brain.active .brain-pulse {
      animation: brain-pulse 2s ease-out infinite;
    }

    .ai-brain.active .brain-pulse.delay-1 {
      animation-delay: 0.5s;
    }

    .ai-brain.active .brain-pulse.delay-2 {
      animation-delay: 1s;
    }

    @keyframes brain-pulse {
      0% {
        transform: scale(1);
        opacity: 0.8;
      }
      100% {
        transform: scale(2.5);
        opacity: 0;
      }
    }

    /* Supplier Nodes */
    .supplier-node {
      position: absolute;
      --distance: 75px;
      left: 50%;
      top: 50%;
      transform: translate(-50%, -50%) rotate(var(--angle)) translateX(var(--distance)) rotate(calc(-1 * var(--angle)));
      opacity: 0;
      transition: opacity 0.5s ease var(--delay);
    }

    .supplier-node.visible {
      opacity: 1;
    }

    .connection-line {
      position: absolute;
      width: 40px;
      height: 2px;
      right: 100%;
      top: 50%;
      transform: translateY(-50%) rotate(calc(180deg + var(--angle)));
      transform-origin: right center;
    }

    .connection-line svg {
      width: 100%;
      height: 100%;
    }

    .connection-line line {
      stroke-dasharray: 100;
      stroke-dashoffset: 100;
    }

    .connection-line line.animated {
      animation: draw-line 0.8s ease forwards;
    }

    @keyframes draw-line {
      to { stroke-dashoffset: 0; }
    }

    .data-particle {
      position: absolute;
      width: 4px;
      height: 4px;
      background: var(--primary-light);
      border-radius: 50%;
      top: 50%;
      transform: translateY(-50%);
      opacity: 0;
    }

    .supplier-node.visible .data-particle {
      animation: particle-move 1.5s ease-in-out infinite;
      animation-delay: var(--delay);
    }

    @keyframes particle-move {
      0% { left: 0; opacity: 0; }
      20% { opacity: 1; }
      80% { opacity: 1; }
      100% { left: 100%; opacity: 0; }
    }

    .node-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
    }

    .supplier-avatar {
      width: 36px;
      height: 36px;
      background: rgba(124, 58, 237, 0.2);
      border: 1px solid var(--primary);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--primary-light);
    }

    .match-score {
      font-size: 0.65rem;
      font-weight: 600;
      color: var(--accent);
      opacity: 0;
      transform: translateY(5px);
      transition: all 0.3s ease;
    }

    .match-score.visible {
      opacity: 1;
      transform: translateY(0);
    }

    /* Offers Mockup (Step 3) */
    .offers-mockup {
      padding: 16px;
    }

    .offers-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .offer-card {
      position: relative;
      display: grid;
      grid-template-columns: 1fr auto auto;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border-subtle);
      border-radius: 10px;
      opacity: 0;
      transform: translateX(-20px);
      transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
      transition-delay: var(--delay);
    }

    .offer-card.visible {
      opacity: 1;
      transform: translateX(0);
    }

    .offer-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
    }

    .offer-card.best {
      border-color: rgba(245, 158, 11, 0.5);
      background: rgba(245, 158, 11, 0.05);
    }

    .offer-card.best:hover {
      box-shadow: 0 4px 24px rgba(245, 158, 11, 0.2);
    }

    .best-badge {
      position: absolute;
      top: -8px;
      right: 12px;
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 2px 8px;
      background: linear-gradient(135deg, var(--accent) 0%, var(--accent-light) 100%);
      border-radius: 4px;
      font-size: 0.6rem;
      font-weight: 700;
      color: #000;
      text-transform: uppercase;
    }

    .best-badge svg {
      width: 10px;
      height: 10px;
    }

    .offer-supplier {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .supplier-logo {
      width: 28px;
      height: 28px;
      background: rgba(124, 58, 237, 0.2);
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.7rem;
      font-weight: 600;
      color: var(--primary-light);
    }

    .supplier-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .supplier-info .name {
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-primary);
    }

    .rating {
      display: flex;
      gap: 1px;
    }

    .rating svg {
      width: 10px;
      height: 10px;
      fill: var(--text-muted);
    }

    .rating svg.filled {
      fill: var(--accent);
    }

    .offer-price {
      font-family: 'Outfit', sans-serif;
      font-size: 1rem;
      font-weight: 700;
      color: var(--text-primary);
    }

    .offer-card.best .offer-price {
      color: var(--accent);
    }

    .select-btn {
      padding: 6px 14px;
      background: transparent;
      border: 1px solid var(--border-subtle);
      border-radius: 6px;
      font-family: 'DM Sans', sans-serif;
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-secondary);
      cursor: pointer;
      transition: all 0.3s ease;
    }

    .select-btn:hover {
      background: var(--primary);
      border-color: var(--primary);
      color: white;
    }

    .select-btn.golden-pulse {
      background: linear-gradient(135deg, var(--accent) 0%, var(--accent-light) 100%);
      border-color: var(--accent);
      color: #000;
      animation: golden-glow 2s ease-in-out infinite;
    }

    @keyframes golden-glow {
      0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
      50% { box-shadow: 0 0 0 8px rgba(245, 158, 11, 0); }
    }

    /* Step Tooltip */
    .step-tooltip {
      position: absolute;
      bottom: -20px;
      left: 50%;
      transform: translateX(-50%);
      width: max-content;
      max-width: 200px;
      padding: 10px 14px;
      background: rgba(0, 0, 0, 0.9);
      border: 1px solid var(--border-subtle);
      border-radius: 8px;
      opacity: 0;
      visibility: hidden;
      transition: all 0.3s ease;
      z-index: 10;
    }

    .step-card:hover .step-tooltip {
      opacity: 1;
      visibility: visible;
      bottom: -10px;
    }

    .step-tooltip p {
      margin: 0;
      font-size: 0.75rem;
      color: var(--text-secondary);
      text-align: center;
      line-height: 1.4;
    }

    .step-tooltip::before {
      content: '';
      position: absolute;
      top: -6px;
      left: 50%;
      transform: translateX(-50%) rotate(45deg);
      width: 10px;
      height: 10px;
      background: rgba(0, 0, 0, 0.9);
      border-left: 1px solid var(--border-subtle);
      border-top: 1px solid var(--border-subtle);
    }

    /* Replay Button */
    .replay-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin: 60px auto 0;
      padding: 14px 28px;
      background: transparent;
      border: 1px solid var(--border-subtle);
      border-radius: 12px;
      font-family: 'DM Sans', sans-serif;
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--text-secondary);
      cursor: pointer;
      opacity: 0;
      transform: translateY(20px);
      transition: all 0.4s ease;
    }

    .replay-btn.visible {
      opacity: 1;
      transform: translateY(0);
    }

    .replay-btn:hover {
      background: rgba(124, 58, 237, 0.1);
      border-color: var(--primary);
      color: var(--primary-light);
    }

    .replay-btn svg {
      width: 20px;
      height: 20px;
    }

    /* Responsive */
    @media (max-width: 1024px) {
      .how-it-works {
        padding: 80px 24px;
      }

      .steps-container {
        gap: 24px;
      }

      .step-card {
        padding: 24px 16px;
      }

      .glass-card {
        max-width: 240px;
      }

      .connector-lines {
        display: none;
      }
    }

    @media (max-width: 768px) {
      .how-it-works {
        padding: 60px 20px;
      }

      .section-header {
        margin-bottom: 48px;
      }

      .timeline-progress {
        margin-bottom: 40px;
      }

      .steps-container {
        grid-template-columns: 1fr;
        gap: 48px;
        max-width: 400px;
      }

      .step-card {
        padding: 32px 24px;
      }

      .step-card::after {
        content: '';
        position: absolute;
        bottom: -24px;
        left: 50%;
        transform: translateX(-50%);
        width: 2px;
        height: 24px;
        background: linear-gradient(180deg, var(--primary) 0%, transparent 100%);
      }

      .step-card:last-child::after {
        display: none;
      }

      .glass-card {
        max-width: 100%;
      }

      .step-tooltip {
        display: none;
      }

      .ai-visualization {
        transform: scale(0.9);
      }
    }

    /* Performance optimizations */
    .step-card,
    .glass-card,
    .offer-card,
    .submit-btn,
    .select-btn {
      will-change: transform, opacity;
    }

    .brain-pulse,
    .data-particle {
      will-change: transform, opacity;
    }
  `],
  animations: [
    trigger('stepAnimation', [
      state('inactive', style({
        opacity: 0.5,
        transform: 'scale(0.95)',
      })),
      state('active', style({
        opacity: 1,
        transform: 'scale(1)',
      })),
      transition('inactive => active', [
        animate('0.6s cubic-bezier(0.16, 1, 0.3, 1)')
      ]),
      transition('active => inactive', [
        animate('0.4s ease-out')
      ]),
    ]),
  ],
})
export class HowItWorksComponent {
  private destroyRef = inject(DestroyRef);

  // State signals
  sectionVisible = signal(false);
  currentStep = signal(0);
  timelineProgress = signal(0);
  animationComplete = signal(false);

  // Step 1 state
  typedDescription = signal('');
  formFieldsVisible = signal(0);

  // Step 2 state
  supplierNodesVisible = signal(0);
  supplierScoresVisible = signal(0);

  // Step 3 state
  offersVisible = signal(0);

  // Connector line offsets (for SVG stroke-dashoffset animation)
  connector1Offset = signal(200);
  connector2Offset = signal(200);

  // Data
  private fullDescription = 'Katuse remont, 120m², Tallinn';

  suppliers = signal([
    { id: 1, initial: 'KE', score: 94, angle: -60 },
    { id: 2, initial: 'MT', score: 87, angle: -20 },
    { id: 3, initial: 'PR', score: 91, angle: 20 },
    { id: 4, initial: 'EH', score: 85, angle: 60 },
    { id: 5, initial: 'TK', score: 89, angle: 100 },
    { id: 6, initial: 'VL', score: 82, angle: 140 },
  ]);

  offers = signal([
    { id: 1, initial: 'KE', name: 'Katuseekspert OÜ', price: '€3,850', rating: 5, isBest: true },
    { id: 2, initial: 'MT', name: 'MeistriTööd AS', price: '€4,200', rating: 4, isBest: false },
    { id: 3, initial: 'PR', name: 'ProRemont OÜ', price: '€5,100', rating: 4, isBest: false },
  ]);

  private animationIntervals: ReturnType<typeof setInterval>[] = [];
  private animationTimeouts: ReturnType<typeof setTimeout>[] = [];

  constructor() {
    afterNextRender(() => {
      // Component is ready for DOM operations
    });
  }

  onSectionVisible() {
    if (this.sectionVisible()) return;
    this.sectionVisible.set(true);
    this.startAnimation();
  }

  private startAnimation() {
    this.resetState();

    // Start Step 1 after initial delay
    const step1Timeout = setTimeout(() => {
      this.runStep1();
    }, 400);
    this.animationTimeouts.push(step1Timeout);
  }

  private runStep1() {
    this.currentStep.set(1);
    this.timelineProgress.set(16);

    // Typing effect
    let charIndex = 0;
    const typingInterval = setInterval(() => {
      if (charIndex <= this.fullDescription.length) {
        this.typedDescription.set(this.fullDescription.substring(0, charIndex));
        charIndex++;
      } else {
        clearInterval(typingInterval);
        // Show additional form fields
        this.showFormFields();
      }
    }, 60);
    this.animationIntervals.push(typingInterval);
  }

  private showFormFields() {
    const field1Timeout = setTimeout(() => {
      this.formFieldsVisible.set(1);
      this.timelineProgress.set(25);
    }, 300);

    const field2Timeout = setTimeout(() => {
      this.formFieldsVisible.set(2);
      this.timelineProgress.set(33);

      // Proceed to Step 2
      const step2Timeout = setTimeout(() => {
        this.runStep2();
      }, 1200);
      this.animationTimeouts.push(step2Timeout);
    }, 600);

    this.animationTimeouts.push(field1Timeout, field2Timeout);
  }

  private runStep2() {
    this.currentStep.set(2);
    this.timelineProgress.set(50);
    this.connector1Offset.set(0);

    // Reveal supplier nodes one by one
    const supplierCount = this.suppliers().length;
    let nodeIndex = 0;

    const nodeInterval = setInterval(() => {
      if (nodeIndex < supplierCount) {
        this.supplierNodesVisible.set(nodeIndex + 1);
        nodeIndex++;
      } else {
        clearInterval(nodeInterval);
        // Show scores
        this.showSupplierScores();
      }
    }, 200);
    this.animationIntervals.push(nodeInterval);
  }

  private showSupplierScores() {
    const supplierCount = this.suppliers().length;
    let scoreIndex = 0;

    const scoreInterval = setInterval(() => {
      if (scoreIndex < supplierCount) {
        this.supplierScoresVisible.set(scoreIndex + 1);
        this.timelineProgress.set(50 + ((scoreIndex + 1) / supplierCount) * 16);
        scoreIndex++;
      } else {
        clearInterval(scoreInterval);

        // Proceed to Step 3
        const step3Timeout = setTimeout(() => {
          this.runStep3();
        }, 1200);
        this.animationTimeouts.push(step3Timeout);
      }
    }, 150);
    this.animationIntervals.push(scoreInterval);
  }

  private runStep3() {
    this.currentStep.set(3);
    this.timelineProgress.set(75);
    this.connector2Offset.set(0);

    // Show offers one by one
    const offerCount = this.offers().length;
    let offerIndex = 0;

    const offerInterval = setInterval(() => {
      if (offerIndex < offerCount) {
        this.offersVisible.set(offerIndex + 1);
        this.timelineProgress.set(75 + ((offerIndex + 1) / offerCount) * 25);
        offerIndex++;
      } else {
        clearInterval(offerInterval);

        // Animation complete
        const completeTimeout = setTimeout(() => {
          this.animationComplete.set(true);

          // Auto-loop after pause
          const loopTimeout = setTimeout(() => {
            if (this.sectionVisible()) {
              this.replayAnimation();
            }
          }, 5000);
          this.animationTimeouts.push(loopTimeout);
        }, 1000);
        this.animationTimeouts.push(completeTimeout);
      }
    }, 300);
    this.animationIntervals.push(offerInterval);
  }

  replayAnimation() {
    this.clearAllTimers();
    this.startAnimation();
  }

  replayFromStep(step: number) {
    this.clearAllTimers();
    this.resetState();
    this.animationComplete.set(false);

    const startTimeout = setTimeout(() => {
      if (step === 1) {
        this.runStep1();
      } else if (step === 2) {
        // Quick setup step 1
        this.currentStep.set(1);
        this.typedDescription.set(this.fullDescription);
        this.formFieldsVisible.set(2);
        this.timelineProgress.set(33);

        setTimeout(() => this.runStep2(), 300);
      } else if (step === 3) {
        // Quick setup steps 1 & 2
        this.currentStep.set(2);
        this.typedDescription.set(this.fullDescription);
        this.formFieldsVisible.set(2);
        this.supplierNodesVisible.set(this.suppliers().length);
        this.supplierScoresVisible.set(this.suppliers().length);
        this.connector1Offset.set(0);
        this.timelineProgress.set(66);

        setTimeout(() => this.runStep3(), 300);
      }
    }, 200);
    this.animationTimeouts.push(startTimeout);
  }

  private resetState() {
    this.currentStep.set(0);
    this.timelineProgress.set(0);
    this.animationComplete.set(false);
    this.typedDescription.set('');
    this.formFieldsVisible.set(0);
    this.supplierNodesVisible.set(0);
    this.supplierScoresVisible.set(0);
    this.offersVisible.set(0);
    this.connector1Offset.set(200);
    this.connector2Offset.set(200);
  }

  private clearAllTimers() {
    this.animationIntervals.forEach(clearInterval);
    this.animationTimeouts.forEach(clearTimeout);
    this.animationIntervals = [];
    this.animationTimeouts = [];
  }

  ngOnDestroy() {
    this.clearAllTimers();
  }
}
