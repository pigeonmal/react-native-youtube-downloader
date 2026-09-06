import type { HybridObject } from 'react-native-nitro-modules';

import type { ExtractStreamOptions } from './types/ExtractStreamOptions';
import type { PlaybackData } from './types/PlaybackTypes';

/**
 * Extracts direct YouTube playback streams for the Android player and downloader.
 */
export interface YoutubeDownloader extends HybridObject<{
  ios: 'swift';
  android: 'kotlin';
}> {
  /**
   * Resolves a selected audio stream and, when requested, a selected video stream.
   */
  extractYoutubeStream(options: ExtractStreamOptions): Promise<PlaybackData>;

  /**
   * Generates a PoToken for a YouTube video when the selected client requires one.
   */
  generatePoToken(videoId: string): Promise<string>;
}
