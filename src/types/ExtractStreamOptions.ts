import type { AudioQuality } from './Quality';

/** Options for one direct YouTube playback extraction request. */
export interface ExtractStreamOptions {
  /** YouTube video ID. */
  videoId: string;
  /** Audio quality preference. */
  audioQuality: AudioQuality;
  /** Optional playlist context used by YouTube playback requests. */
  playlistId?: string;
  /** Optional maximum video height. Omit this field for audio-only playback. */
  videoQuality?: number;
  /** Optional authenticated YouTube cookie header. */
  cookie?: string;
  /** Optional visitor data override. */
  forceVisitorData?: string;
  /** When true, anonymous clients are not attempted. */
  authenticatedOnly?: boolean;
  /** Optional specific client name to target (e.g. 'IOS', 'ANDROID_VR', 'WEB', 'MWEB', 'TVHTML5_SIMPLY'). */
  clientName?: string;
}
