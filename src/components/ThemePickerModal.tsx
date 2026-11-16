import React from 'react';
import { Check, Moon, Sun, Monitor, Palette, X } from 'lucide-react';
import { usePabaru } from '../state/PabaruContext';
import { PABARU_THEMES } from '../theme/tokens';
import { AppColorThemeId, ThemeMode } from '../types/models';

interface ThemePickerModalProps {
  open: boolean;
  onClose: () => void;
}

export const ThemePickerModal: React.FC<ThemePickerModalProps> = ({ open, onClose }) => {
  const { state, setThemeId, setThemeMode } = usePabaru();
  if (!open) return null;

  const modes: Array<{ id: ThemeMode; label: string; icon: React.ReactNode }> = [
    { id: 'auto', label: 'System Auto', icon: <Monitor className="w-3.5 h-3.5" /> },
    { id: 'light', label: 'Light Mode', icon: <Sun className="w-3.5 h-3.5" /> },
    { id: 'dark', label: 'Dark Mode', icon: <Moon className="w-3.5 h-3.5" /> },
  ];

  const themeIds: AppColorThemeId[] = [
    'clinic_blue',
    'fresh_mint',
    'warm_sand',
    'midnight_teal',
    'royal_purple',
    'amoled_black',
  ];

  return (
    <div className="no-print fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-lg rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)]">
          <div className="flex items-center gap-2">
            <Palette className="w-4 h-4 text-[var(--p-primary)]" />
            <h3 className="text-sm font-semibold text-[var(--p-text)]">Appearance &amp; Theme</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-[var(--p-text-muted)] hover:bg-[var(--p-card)]"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 space-y-5">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-[var(--p-text-muted)] mb-2">
              Brightness Mode
            </p>
            <div className="grid grid-cols-3 gap-2">
              {modes.map((m) => {
                const active = state.themeMode === m.id;
                return (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => setThemeMode(m.id)}
                    className={`flex items-center justify-center gap-1.5 whitespace-nowrap rounded-xl border px-3 py-2 text-xs font-medium transition ${
                      active
                        ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                        : 'border-[var(--p-card-border)] bg-[var(--p-bg)] text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]'
                    }`}
                  >
                    {m.icon}
                    <span>{m.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-[var(--p-text-muted)] mb-2">
              Color Palette (WCAG AA Verified)
            </p>
            <div className="grid grid-cols-2 gap-2.5">
              {themeIds.map((id) => {
                const th = PABARU_THEMES[id];
                const selected = state.activeThemeId === id;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setThemeId(id)}
                    className={`flex items-center justify-between gap-2 rounded-xl border p-3 text-left transition ${
                      selected
                        ? 'border-[var(--p-primary)] ring-1 ring-[var(--p-primary)] bg-[var(--p-secondary-bg)]'
                        : 'border-[var(--p-card-border)] bg-[var(--p-bg)] hover:bg-[var(--p-secondary-bg)]'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div
                        className="w-8 h-8 rounded-lg border border-black/15 shrink-0 flex items-center justify-center"
                        style={{ backgroundColor: th.bg }}
                      >
                        <div
                          className="w-4 h-4 rounded-full"
                          style={{ backgroundColor: th.primary }}
                        />
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-[var(--p-text)] truncate">
                          {th.displayName}
                        </p>
                        <p className="text-[11px] text-[var(--p-text-muted)] truncate">
                          {th.subtitle}
                        </p>
                      </div>
                    </div>
                    {selected && <Check className="w-4 h-4 text-[var(--p-primary)] shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
