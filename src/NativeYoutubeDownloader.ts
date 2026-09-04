import { TurboModuleRegistry, type TurboModule } from 'react-native';

export interface PlaybackData {
  audioConfig?: AudioConfig;
  videoDetails?: VideoDetails;
  playbackTracking?: PlaybackTracking;
  streamExpiresInSeconds: number;
  audioStream: StreamPlayback;
  videoStream?: StreamPlayback;
  clientName: string;
  extractionDurationMs?: number;
  poTokenDurationMs?: number;
}

export interface StreamPlayback {
  format: StreamFormat;
  streamUrl: string;
}

export interface AudioConfig {
  loudnessDb?: number;
  perceptualLoudnessDb?: number;
}

export interface StreamFormat {
  itag: number;
  mimeType: string;
  bitrate: number;
  width?: number;
  height?: number;
  contentLength?: number;
  quality: string;
  fps?: number;
  qualityLabel?: string;
  approxDurationMs?: string;
  audioSampleRate?: number;
  audioChannels?: number;
  loudnessDb?: number;
}

export interface VideoDetails {
  videoId: string;
  title?: string;
  author?: string;
  channelId: string;
  lengthSeconds: string;
  musicVideoType?: string;
  viewCount?: string;
}

export interface PlaybackTracking {
  videostatsPlaybackUrl?: VideostatsPlaybackUrl;
  videostatsWatchtimeUrl?: VideostatsWatchtimeUrl;
  atrUrl?: AtrUrl;
}

interface VideostatsPlaybackUrl {
  baseUrl?: string;
}

interface VideostatsWatchtimeUrl {
  baseUrl?: string;
}

interface AtrUrl {
  baseUrl?: string;
}

export interface ExtractStreamProps {
  videoId: string;
  audioQuality: string;
  playlistId?: string;
  videoQuality?: number;
  cookie?: string;
  forceVisitorData?: string;
  /** When true, never attempt anonymous YouTube clients. */
  authenticatedOnly?: boolean;
}

export interface Spec extends TurboModule {
  /**
   * Extract YouTube stream with specified audio/video preferences
   * @param options Download configuration
   * @returns Promise resolving to stream response with URLs and metadata
   * @throws Error if stream retrieval fails
   */
  extractYoutubeStream(options: ExtractStreamProps): Promise<PlaybackData>;
  /**
   * Generates a YouTube player PoToken for a given video ID
   * @param videoId Target video ID
   * @returns Promise resolving to the base64-encoded PoToken string
   */
  generatePoToken(videoId: string): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('YoutubeDownloader');
