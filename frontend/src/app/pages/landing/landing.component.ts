import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { environment } from '../../../environments/environment';
import { HowItWorksComponent } from '../../components/how-it-works/how-it-works.component';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, HowItWorksComponent],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss']
})
export class LandingComponent implements OnInit {
  isAnnual = signal(false);
  currentLang = signal('ET');
  mobileMenuOpen = signal(false);
  supplierCount = signal(500);

  languages = ['ET', 'EN', 'RU'];

  constructor(
    private translate: TranslateService,
    private http: HttpClient
  ) {
    const savedLang = localStorage.getItem('buildquote_lang') || 'ET';
    this.currentLang.set(savedLang);
    this.translate.use(savedLang.toLowerCase());
  }

  ngOnInit(): void {
    this.loadSupplierCount();
  }

  loadSupplierCount(): void {
    this.http.get<any>(`${environment.apiUrl}/batch/stats`).subscribe({
      next: (stats) => {
        if (stats.totalCompanies) {
          this.supplierCount.set(stats.totalCompanies);
        }
      },
      error: () => {
        // Keep default 500
      }
    });
  }

  painPoints = [
    {
      icon: '😤',
      title: 'Tarnijate otsimine võtab tunde',
      description: 'Helistad, kirjutad, ootad vastuseid. Iga projekt algab nullist.'
    },
    {
      icon: '📊',
      title: 'Pakkumiste võrdlemine on kaos',
      description: 'Exceli tabelid, meilid, erinevad formaadid. Kust sa tead, mis on parim hind?'
    },
    {
      icon: '⏰',
      title: 'Tähtajad põlevad, projektid venivad',
      description: 'Hankefaas võtab nädalaid, ehkki töö peaks juba käima.'
    }
  ];

  steps = [
    {
      number: '01',
      icon: '✍️',
      title: 'Kirjelda projekti',
      description: 'Sisesta töö kirjeldus tekstina või lae üles fail (PDF, DWG, IFC). AI analüüsib ja tuvastab etapid automaatselt.'
    },
    {
      number: '02',
      icon: '🤖',
      title: 'AI leiab tegijad',
      description: 'Süsteem sobitab projekti sobivate tarnijatega andmebaasist ja saadab hinnapäringud automaatselt.'
    },
    {
      number: '03',
      icon: '✅',
      title: 'Võrdle ja vali',
      description: 'Saa pakkumised ühte kohta, võrdle hindu ja tingimusi, vali parim tegija.'
    }
  ];

  features = [
    {
      icon: '🏢',
      title: 'Tarnijate andmebaas',
      description: 'Üle 31,000 ehitusettevõtte kogu Eestis. Otsing kategooria ja asukoha järgi.',
      tag: null
    },
    {
      icon: '📋',
      title: 'Hinnapäringute mallid',
      description: 'Professionaalsed mallid, mis sisaldavad kõiki olulisi detaile. Saada päringud ühe klikiga.',
      tag: null
    },
    {
      icon: '📊',
      title: 'Pakkumiste võrdlus',
      description: 'Kõik pakkumised ühes vaates. Võrdle hindu, tähtaegu ja tingimusi kõrvuti.',
      tag: null
    },
    {
      icon: '🤖',
      title: 'AI projekti analüüs',
      description: 'Lae üles PDF, DWG või IFC fail. AI tuvastab ehitusetapid, kogused ja materjalid automaatselt.',
      tag: 'UUS'
    },
    {
      icon: '💰',
      title: 'Turuhinna kontroll',
      description: 'Näe kohe, kas pakkumine on turuhinnaga kooskõlas. Ära maksa üle.',
      tag: 'UUS'
    },
    {
      icon: '⚡',
      title: 'Automaatne hinnapäring',
      description: 'AI saadab hinnapäringud kõigile sobivate tarnijatele. Sina keskendud ehitamisele.',
      tag: 'UUS'
    }
  ];

  pricingPlans = [
    {
      name: 'Tasuta',
      description: 'Alustamiseks ja väikestele projektidele',
      monthlyPrice: 0,
      annualPrice: 0,
      features: [
        '3 projekti kuus',
        '10 hinnapäringut kuus',
        'Põhiline tarnijate otsing',
        'E-posti tugi'
      ],
      cta: 'Alusta tasuta',
      highlighted: false
    },
    {
      name: 'Pro',
      description: 'Aktiivsetele ehitajatele ja väikefirmadele',
      monthlyPrice: 49,
      annualPrice: 39,
      features: [
        'Piiramatu arv projekte',
        'Piiramatu hinnapäringud',
        'AI projekti analüüs',
        'Turuhinna kontroll',
        'Prioriteetne tugi',
        'Pakkumiste eksport'
      ],
      cta: 'Alusta Pro plaaniga',
      highlighted: true
    },
    {
      name: 'Ettevõte',
      description: 'Suurtele ehitusettevõtetele ja arendajatele',
      monthlyPrice: 199,
      annualPrice: 159,
      features: [
        'Kõik Pro funktsioonid',
        'Meeskonna haldus',
        'API ligipääs',
        'Kohandatud integratsioonid',
        'Dedikeeritud kontohaldur',
        'SLA garantii'
      ],
      cta: 'Võta ühendust',
      highlighted: false
    }
  ];

  faqItems = [
    {
      question: 'Kuidas BuildQuote töötab?',
      answer: 'Kirjelda oma ehitusprojekti või lae üles fail (PDF, DWG, IFC). Meie AI analüüsib projekti, tuvastab tööetapid ja leiab sobivad tarnijad. Saadame hinnapäringud automaatselt ja kogume pakkumised ühte kohta.',
      open: false
    },
    {
      question: 'Kas see on tõesti tasuta?',
      answer: 'Jah! Tasuta pakett võimaldab teha kuni 3 projekti ja 10 hinnapäringut kuus. See sobib väiksematele projektidele. Suurema mahu jaoks on Pro ja Ettevõte paketid.',
      open: false
    },
    {
      question: 'Millised failiformaadid on toetatud?',
      answer: 'Toetame PDF, DOCX, TXT dokumente, AutoCAD DWG/DXF jooniseid, BIM/IFC mudeleid ja pilte (JPG, PNG). AI analüüsib faili ja tuvastab ehitusetapid automaatselt.',
      open: false
    },
    {
      question: 'Kui kiiresti saan pakkumised?',
      answer: 'Hinnapäringud saadetakse koheselt pärast projekti loomist. Pakkumised hakkavad laekuma tavaliselt 24-48 tunni jooksul, olenevalt tarnijate reageerimiskiirusest.',
      open: false
    },
    {
      question: 'Kas mu andmed on turvatud?',
      answer: 'Absoluutselt. Kasutame pangataseme krüpteeringut (SSL/TLS) ja GDPR-iga ühilduvat andmetöötlust. Su projektid ja äriinfo on konfidentsiaalsed.',
      open: false
    }
  ];

  toggleBilling(): void {
    this.isAnnual.update(v => !v);
  }

  setLanguage(lang: string): void {
    this.currentLang.set(lang);
    this.translate.use(lang.toLowerCase());
    localStorage.setItem('buildquote_lang', lang);
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen.update(v => !v);
  }

  toggleFaq(index: number): void {
    this.faqItems[index].open = !this.faqItems[index].open;
  }

  scrollTo(elementId: string): void {
    const element = document.getElementById(elementId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' });
    }
    this.mobileMenuOpen.set(false);
  }

  getPrice(plan: typeof this.pricingPlans[0]): number {
    return this.isAnnual() ? plan.annualPrice : plan.monthlyPrice;
  }

  getSavings(plan: typeof this.pricingPlans[0]): number {
    if (plan.monthlyPrice === 0) return 0;
    return Math.round((1 - plan.annualPrice / plan.monthlyPrice) * 100);
  }
}
