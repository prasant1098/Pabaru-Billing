import { AppColorThemeId, ThemeMode } from '../types/models';

export interface PabaruTokens {
  id: AppColorThemeId;
  displayName: string;
  subtitle: string;
  isDark: boolean;
  bg: string;
  headerBg: string;
  headerText: string;
  card: string;
  cardBorder: string;
  text: string;
  textMuted: string;
  primary: string;
  primaryText: string;
  secondaryBg: string;
  accent: string;
  accentTint: string;
  link: string;
  navBg: string;
  navBorder: string;
  navActive: string;
  navInactive: string;
  success: string;
  successContainer: string;
  onSuccessContainer: string;
  danger: string;
  dangerContainer: string;
  onDangerContainer: string;
  warning: string;
  warningContainer: string;
  onWarningContainer: string;
  info: string;
  infoContainer: string;
  onInfoContainer: string;
}

function withSemantic(
  base: Omit<
    PabaruTokens,
    | 'success'
    | 'successContainer'
    | 'onSuccessContainer'
    | 'danger'
    | 'dangerContainer'
    | 'onDangerContainer'
    | 'warning'
    | 'warningContainer'
    | 'onWarningContainer'
    | 'info'
    | 'infoContainer'
    | 'onInfoContainer'
  >
): PabaruTokens {
  const d = base.isDark;
  return {
    ...base,
    success: d ? '#34D399' : '#047857',
    successContainer: d ? '#064E3B' : '#D1FAE5',
    onSuccessContainer: d ? '#6EE7B7' : '#065F46',
    danger: d ? '#F87171' : '#DC2626',
    dangerContainer: d ? '#450A0A' : '#FEE2E2',
    onDangerContainer: d ? '#FCA5A5' : '#991B1B',
    warning: d ? '#F59E0B' : '#B45309',
    warningContainer: d ? '#451A03' : '#FEF3C7',
    onWarningContainer: d ? '#FCD34D' : '#92400E',
    info: d ? '#38BDF8' : '#0284C7',
    infoContainer: d ? '#0C4A6E' : '#E0F2FE',
    onInfoContainer: d ? '#7DD3FC' : '#075985',
  };
}

export const PABARU_THEMES: Record<AppColorThemeId, PabaruTokens> = {
  midnight_teal: withSemantic({
    id: 'midnight_teal',
    displayName: 'Midnight Teal',
    subtitle: 'Executive dark slate & teal',
    isDark: true,
    bg: '#0F172A',
    headerBg: '#0F172A',
    headerText: '#FFFFFF',
    card: '#1E293B',
    cardBorder: '#334155',
    text: '#F8FAFC',
    textMuted: '#94A3B8',
    primary: '#0D9488',
    primaryText: '#FFFFFF',
    secondaryBg: '#132238',
    accent: '#14B8A6',
    accentTint: '#134E4A',
    link: '#38BDF8',
    navBg: '#0F172A',
    navBorder: '#334155',
    navActive: '#14B8A6',
    navInactive: '#94A3B8',
  }),
  clinic_blue: withSemantic({
    id: 'clinic_blue',
    displayName: 'Clinic Blue',
    subtitle: 'Crisp corporate navy & teal',
    isDark: false,
    bg: '#F3F6FB',
    headerBg: '#1E3A8A',
    headerText: '#FFFFFF',
    card: '#FFFFFF',
    cardBorder: '#E3E9F3',
    text: '#0F1F4D',
    textMuted: '#5B6B8C',
    primary: '#1E3A8A',
    primaryText: '#FFFFFF',
    secondaryBg: '#E8EEF8',
    accent: '#0F9D8A',
    accentTint: '#CCFBF1',
    link: '#1E3A8A',
    navBg: '#FFFFFF',
    navBorder: '#E3E9F3',
    navActive: '#1E3A8A',
    navInactive: '#64748B',
  }),
  fresh_mint: withSemantic({
    id: 'fresh_mint',
    displayName: 'Fresh Mint',
    subtitle: 'Botanical teal daylight mode',
    isDark: false,
    bg: '#F0FAF8',
    headerBg: '#0F766E',
    headerText: '#FFFFFF',
    card: '#FFFFFF',
    cardBorder: '#D5EEE9',
    text: '#0B3B36',
    textMuted: '#55756F',
    primary: '#0F766E',
    primaryText: '#FFFFFF',
    secondaryBg: '#E0F4F0',
    accent: '#14B8A6',
    accentTint: '#CCFBF1',
    link: '#0F766E',
    navBg: '#FFFFFF',
    navBorder: '#D5EEE9',
    navActive: '#0F766E',
    navInactive: '#55756F',
  }),
  warm_sand: withSemantic({
    id: 'warm_sand',
    displayName: 'Warm Sand',
    subtitle: 'Traditional khata paper & terracotta',
    isDark: false,
    bg: '#FBF7F2',
    headerBg: '#9A3412',
    headerText: '#FFFFFF',
    card: '#FFFFFF',
    cardBorder: '#EFE3D3',
    text: '#3B2412',
    textMuted: '#7A6652',
    primary: '#C2410C',
    primaryText: '#FFFFFF',
    secondaryBg: '#F5EBE1',
    accent: '#D97706',
    accentTint: '#FEF3C7',
    link: '#C2410C',
    navBg: '#FFFFFF',
    navBorder: '#EFE3D3',
    navActive: '#C2410C',
    navInactive: '#7A6652',
  }),
  royal_purple: withSemantic({
    id: 'royal_purple',
    displayName: 'Royal Purple',
    subtitle: 'Deep indigo dark workspace',
    isDark: true,
    bg: '#14101F',
    headerBg: '#1E1830',
    headerText: '#F1ECFF',
    card: '#1E1830',
    cardBorder: '#2E2547',
    text: '#F1ECFF',
    textMuted: '#A99BC9',
    primary: '#8B5CF6',
    primaryText: '#FFFFFF',
    secondaryBg: '#292140',
    accent: '#C4B5FD',
    accentTint: '#4C1D95',
    link: '#A78BFA',
    navBg: '#14101F',
    navBorder: '#2E2547',
    navActive: '#C4B5FD',
    navInactive: '#A99BC9',
  }),
  amoled_black: withSemantic({
    id: 'amoled_black',
    displayName: 'AMOLED Black',
    subtitle: 'Pure contrast OLED dark mode',
    isDark: true,
    bg: '#000000',
    headerBg: '#000000',
    headerText: '#FFFFFF',
    card: '#0B0B0B',
    cardBorder: '#1F1F1F',
    text: '#FFFFFF',
    textMuted: '#9CA3AF',
    primary: '#14B8A6',
    primaryText: '#000000',
    secondaryBg: '#121212',
    accent: '#2DD4BF',
    accentTint: '#134E4A',
    link: '#2DD4BF',
    navBg: '#000000',
    navBorder: '#1F1F1F',
    navActive: '#2DD4BF',
    navInactive: '#9CA3AF',
  }),
};

export function resolveEffectiveTokens(
  themeId: AppColorThemeId,
  themeMode: ThemeMode,
  prefersDark: boolean
): PabaruTokens {
  if (themeMode === 'auto') {
    return prefersDark ? PABARU_THEMES.midnight_teal : PABARU_THEMES.clinic_blue;
  }
  const chosen = PABARU_THEMES[themeId] ?? PABARU_THEMES.clinic_blue;
  if (themeMode === 'dark' && !chosen.isDark) return PABARU_THEMES.midnight_teal;
  if (themeMode === 'light' && chosen.isDark) return PABARU_THEMES.clinic_blue;
  return chosen;
}

export function applyTokensToDocument(tokens: PabaruTokens): void {
  const root = document.documentElement;
  if (tokens.isDark) {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
  const vars: Record<string, string> = {
    '--p-bg': tokens.bg,
    '--p-header-bg': tokens.headerBg,
    '--p-header-text': tokens.headerText,
    '--p-card': tokens.card,
    '--p-card-border': tokens.cardBorder,
    '--p-text': tokens.text,
    '--p-text-muted': tokens.textMuted,
    '--p-primary': tokens.primary,
    '--p-primary-text': tokens.primaryText,
    '--p-secondary-bg': tokens.secondaryBg,
    '--p-accent': tokens.accent,
    '--p-accent-tint': tokens.accentTint,
    '--p-link': tokens.link,
    '--p-nav-bg': tokens.navBg,
    '--p-nav-border': tokens.navBorder,
    '--p-nav-active': tokens.navActive,
    '--p-nav-inactive': tokens.navInactive,
    '--p-success': tokens.success,
    '--p-success-container': tokens.successContainer,
    '--p-on-success-container': tokens.onSuccessContainer,
    '--p-danger': tokens.danger,
    '--p-danger-container': tokens.dangerContainer,
    '--p-on-danger-container': tokens.onDangerContainer,
    '--p-warning': tokens.warning,
    '--p-warning-container': tokens.warningContainer,
    '--p-on-warning-container': tokens.onWarningContainer,
    '--p-info': tokens.info,
    '--p-info-container': tokens.infoContainer,
    '--p-on-info-container': tokens.onInfoContainer,
  };
  for (const [k, v] of Object.entries(vars)) {
    root.style.setProperty(k, v);
  }
}
