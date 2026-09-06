/** The complete direct playback response returned by the native extractor. */
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

/** A selected stream and the headers/range contract required to read it. */
export interface StreamPlayback {
  format: StreamFormat;
  streamUrl: string;
  /** HTTP headers to preserve for stream reads and downloads. */
  requestHeaders?: Record<string, string>;
  /** Preferred bounded-read size in bytes. */
  rangeChunkSizeBytes?: number;
}

/** Metadata describing one selected YouTube format. */
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

/** Normalization metadata supplied by YouTube. */
export interface AudioConfig {
  loudnessDb?: number;
  perceptualLoudnessDb?: number;
}

/** Basic YouTube video metadata. */
export interface VideoDetails {
  videoId: string;
  title?: string;
  author?: string;
  channelId: string;
  lengthSeconds: string;
  musicVideoType?: string;
  viewCount?: string;
}

/** Optional YouTube playback tracking URLs. */
export interface PlaybackTracking {
  videostatsPlaybackUrl?: TrackingUrl;
  videostatsWatchtimeUrl?: TrackingUrl;
  atrUrl?: TrackingUrl;
}

/** One playback tracking endpoint. */
export interface TrackingUrl {
  baseUrl?: string;
}
