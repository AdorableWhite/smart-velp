import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { AppearanceMode, SubtitleMode } from '../types/media';

interface PreferencesState {
  appearance: AppearanceMode;
  subtitleMode: SubtitleMode;
  playbackRate: number;
  fontSize: number;
  autoDownload: boolean;
  loopCurrentLine: boolean;
  sourceLang: string;
  targetLang: string;
  selectedProfileId?: string;
  setAppearance: (appearance: AppearanceMode) => void;
  setSubtitleMode: (mode: SubtitleMode) => void;
  setPlaybackRate: (rate: number) => void;
  setFontSize: (size: number) => void;
  setAutoDownload: (enabled: boolean) => void;
  setLoopCurrentLine: (enabled: boolean) => void;
  setLanguagePair: (sourceLang: string, targetLang: string) => void;
  setSelectedProfileId: (profileId?: string) => void;
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      appearance: 'system',
      subtitleMode: 'dual',
      playbackRate: 1,
      fontSize: 22,
      autoDownload: false,
      loopCurrentLine: false,
      sourceLang: 'en',
      targetLang: 'zh-CN',
      selectedProfileId: undefined,
      setAppearance: (appearance) => set({ appearance }),
      setSubtitleMode: (subtitleMode) => set({ subtitleMode }),
      setPlaybackRate: (playbackRate) => set({ playbackRate }),
      setFontSize: (fontSize) => set({ fontSize }),
      setAutoDownload: (autoDownload) => set({ autoDownload }),
      setLoopCurrentLine: (loopCurrentLine) => set({ loopCurrentLine }),
      setLanguagePair: (sourceLang, targetLang) => set({ sourceLang, targetLang }),
      setSelectedProfileId: (selectedProfileId) => set({ selectedProfileId })
    }),
    {
      name: 'smart-velp.preferences',
      storage: createJSONStorage(() => localStorage)
    }
  )
);

