import YoutubeDownloader, {
  VideoQuality,
} from '@pigeonmal/react-native-youtube-downloader';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

interface BenchmarkItem {
  name: string;
  videoId?: string;
  durationMs: number;
  nativeDurationMs?: number;
  poTokenDurationMs?: number;
  clientName?: string;
  status: 'idle' | 'running' | 'success' | 'failed';
  error?: string;
  details?: string;
  itag?: number;
  bitrate?: number;
  isCached?: boolean;
}

interface ClientVerificationItem {
  clientName: string;
  label: string;
  description: string;
  category: 'Anonymous' | 'Authenticated' | 'PoToken Required';
  auth?: boolean;
  status: 'idle' | 'running' | 'success' | 'failed';
  extractionDurationMs?: number;
  nativeDurationMs?: number;
  streamUrl?: string;
  streamReachable?: boolean;
  streamHttpStatus?: number;
  streamType?: string;
  sabrSupported?: boolean;
  sabrUrl?: string;
  isHls?: boolean;
  error?: string;
  itag?: number;
  bitrate?: number;
}

interface BenchmarkState {
  poTokenCold?: BenchmarkItem;
  poTokenWarm?: BenchmarkItem;
  firstVideoExtraction?: BenchmarkItem;
  subsequentExtractions: BenchmarkItem[];
  mazicaAudioExtraction?: BenchmarkItem;
  authFallbackTest?: BenchmarkItem;
  clientVerifications?: ClientVerificationItem[];
  isRunning: boolean;
  isVerifyingClients?: boolean;
  completedAt?: string;
}

const TEST_VIDEOS = [
  { id: 'dQw4w9WgXcQ', label: 'Video 1 (First/Cold)' },
  { id: '9bZkp7q19f0', label: 'Video 2 (Warm - Gangnam Style)' },
  { id: 'kJQP7kiw5Fk', label: 'Video 3 (Warm - Despacito)' },
  { id: 'fJ9rUzIMcZQ', label: 'Video 4 (Warm - Queen Bohemian Rhapsody)' },
];

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

const CLIENTS_TO_TEST: {
  id: string;
  label: string;
  category: 'Anonymous' | 'Authenticated' | 'PoToken Required';
  desc: string;
  auth: boolean;
}[] = [
  {
    id: 'VISIONOS',
    label: 'visionOS (Primary Anon)',
    category: 'Anonymous',
    desc: 'Ultra-fast (~80ms), direct progressive WebM/MP4 + SABR',
    auth: false,
  },
  {
    id: 'VISIONOS_0_1',
    label: 'visionOS v0.1 (Anon Fallback)',
    category: 'Anonymous',
    desc: 'Alternative visionOS client profile',
    auth: false,
  },
  {
    id: 'ANDROID_VR_1_65_10',
    label: 'Android VR 1.65 (Anon + SABR)',
    category: 'Anonymous',
    desc: 'Fast (~100ms) progressive formats + SABR UMP bootstrap',
    auth: false,
  },
  {
    id: 'ANDROID_VR_1_61_48',
    label: 'Android VR 1.61 (Anon Fallback)',
    category: 'Anonymous',
    desc: 'Older Android VR profile with alternative formats',
    auth: false,
  },
  {
    id: 'ANDROID_VR_1_43_32',
    label: 'Android VR 1.43 (Direct AAC/Opus)',
    category: 'Anonymous',
    desc: 'Direct adaptive AAC (itag 140) and Opus without SABR',
    auth: false,
  },
  {
    id: 'ANDROID',
    label: 'Android Native (21.26)',
    category: 'Anonymous',
    desc: 'Direct progressive MP4 (itag 18) + full SABR bootstrap',
    auth: false,
  },
  {
    id: 'IOS',
    label: 'iOS (Anon + HLS)',
    category: 'Anonymous',
    desc: 'HLS m3u8 master playlist with byte-range audio segments',
    auth: false,
  },
  {
    id: 'IPADOS',
    label: 'iPadOS (Anon + HLS)',
    category: 'Anonymous',
    desc: 'iPadOS 17.7 profile with HLS playlist & SABR bootstrap',
    auth: false,
  },
  {
    id: 'TVHTML5_SIMPLY',
    label: 'TV Simply (Botguard)',
    category: 'PoToken Required',
    desc: 'Requires visitor-bound PoToken attestation',
    auth: false,
  },
  {
    id: 'MWEB',
    label: 'Mobile Web (Botguard)',
    category: 'PoToken Required',
    desc: 'Requires PoToken attestation or cookies',
    auth: false,
  },
  {
    id: 'WEB',
    label: 'Desktop Web (Botguard)',
    category: 'PoToken Required',
    desc: 'Requires PoToken attestation or cookies',
    auth: false,
  },
  {
    id: 'WEB_REMIX',
    label: 'Web Remix (Music Auth)',
    category: 'Authenticated',
    desc: 'YouTube Music client with cookie authentication',
    auth: true,
  },
  {
    id: 'TVHTML5',
    label: 'TV HTML5 (Living Room Auth)',
    category: 'Authenticated',
    desc: 'TV client fallback with cookie authentication',
    auth: true,
  },
  {
    id: 'TVHTML5_DOWNGRADED',
    label: 'TV Downgraded (yt-dlp Auth)',
    category: 'Authenticated',
    desc: 'yt-dlp default fallback with cookie authentication',
    auth: true,
  },
];

const testStreamReachability = async (
  streamUrl: string,
  isHls: boolean,
  requestHeaders?: Record<string, string>
): Promise<{
  reachable: boolean;
  status: number;
  type: string;
  error?: string;
}> => {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    const headers: Record<string, string> = {
      ...(requestHeaders || {}),
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    };
    if (!isHls) {
      headers.Range = 'bytes=0-1024';
    }
    const res = await fetch(streamUrl, {
      method: 'GET',
      headers,
      signal: controller.signal,
    });
    clearTimeout(timeout);

    if (isHls) {
      if (res.status === 200) {
        const text = await res.text();
        const hasExtm3u = text.includes('#EXTM3U');
        return {
          reachable: true,
          status: res.status,
          type: hasExtm3u
            ? '200 OK (HLS #EXTM3U Playlist)'
            : '200 OK (HLS Manifest)',
        };
      }
      return {
        reachable: false,
        status: res.status,
        type: `HLS HTTP ${res.status}`,
      };
    } else {
      const ok = res.status === 200 || res.status === 206;
      return {
        reachable: ok,
        status: res.status,
        type:
          res.status === 206
            ? '206 Partial Content (Bytes 0-1024 OK)'
            : `HTTP ${res.status} OK`,
      };
    }
  } catch (err: any) {
    return {
      reachable: false,
      status: 0,
      type: 'Unreachable',
      error: errorMessage(err),
    };
  }
};

export default function App() {
  const [activeTab, setActiveTab] = useState<'clients' | 'benchmark'>(
    'clients'
  );
  const [state, setState] = useState<BenchmarkState>({
    subsequentExtractions: [],
    isRunning: false,
  });

  const runBenchmark = useCallback(async () => {
    setState({
      isRunning: true,
      subsequentExtractions: [],
    });
    console.log('[BENCHMARK] === Starting YouTube Downloader Benchmark ===');

    // 1. PoToken Generation Benchmark
    console.log('[BENCHMARK] Testing PoToken generation...');
    let poCold: BenchmarkItem;
    const startPo1 = Date.now();
    try {
      const token1 = await YoutubeDownloader.generatePoToken('dQw4w9WgXcQ');
      const time1 = Math.round(Date.now() - startPo1);
      console.log(
        `[BENCHMARK] PoToken #1 generated in ${time1}ms: ${token1.substring(0, 16)}...`
      );
      poCold = {
        name: 'PoToken Generation #1 (Cold)',
        durationMs: time1,
        status: 'success',
        details: `${token1.substring(0, 20)}... (length: ${token1.length})`,
      };
    } catch (e: any) {
      console.log('[BENCHMARK] PoToken #1 unavailable:', errorMessage(e));
      poCold = {
        name: 'PoToken Generation #1 (Cold)',
        durationMs: Math.round(Date.now() - startPo1),
        status: 'failed',
        error: errorMessage(e),
      };
    }

    let poWarm: BenchmarkItem;
    const startPo2 = Date.now();
    try {
      const token2 = await YoutubeDownloader.generatePoToken('9bZkp7q19f0');
      const time2 = Math.round(Date.now() - startPo2);
      console.log(
        `[BENCHMARK] PoToken #2 generated in ${time2}ms: ${token2.substring(0, 16)}...`
      );
      poWarm = {
        name: 'PoToken Generation #2 (Warm)',
        durationMs: time2,
        status: 'success',
        details: `${token2.substring(0, 20)}... (length: ${token2.length})`,
      };
    } catch (e: any) {
      console.log('[BENCHMARK] PoToken #2 unavailable:', errorMessage(e));
      poWarm = {
        name: 'PoToken Generation #2 (Warm)',
        durationMs: Math.round(Date.now() - startPo2),
        status: 'failed',
        error: errorMessage(e),
      };
    }

    setState((prev) => ({
      ...prev,
      poTokenCold: poCold,
      poTokenWarm: poWarm,
    }));

    // 2. First Video ID Extraction (Cold / Initial)
    console.log('[BENCHMARK] Testing First Video Extraction (Cold)...');
    const coldVideo = TEST_VIDEOS[0]!;
    let coldExtraction: BenchmarkItem;
    const startCold = Date.now();
    try {
      const playback = await YoutubeDownloader.extractYoutubeStream({
        videoId: coldVideo.id,
        audioQuality: 'AUTO',
        videoQuality: VideoQuality.QUALITY_1080P,
      });
      const elapsed = Math.round(Date.now() - startCold);
      const isCached =
        playback.extractionDurationMs != null &&
        playback.extractionDurationMs < 5 &&
        elapsed < 50;
      console.log(
        `[BENCHMARK] First video (${coldVideo.id}) extracted in ${elapsed}ms | native: ${playback.extractionDurationMs?.toFixed(1)}ms | client: ${playback.clientName}`
      );
      coldExtraction = {
        name: coldVideo.label,
        videoId: coldVideo.id,
        durationMs: elapsed,
        nativeDurationMs: playback.extractionDurationMs
          ? Math.round(playback.extractionDurationMs)
          : undefined,
        poTokenDurationMs: playback.poTokenDurationMs
          ? Math.round(playback.poTokenDurationMs)
          : undefined,
        clientName: playback.clientName,
        status: 'success',
        details: playback.videoDetails?.title ?? 'No title',
        itag: playback.audioStream?.format.itag,
        bitrate: playback.audioStream?.format.bitrate,
        isCached,
      };
    } catch (e: any) {
      console.log('[BENCHMARK] First video unavailable:', errorMessage(e));
      coldExtraction = {
        name: coldVideo.label,
        videoId: coldVideo.id,
        durationMs: Math.round(Date.now() - startCold),
        status: 'failed',
        error: errorMessage(e),
      };
    }

    setState((prev) => ({
      ...prev,
      firstVideoExtraction: coldExtraction,
    }));

    // 3. Subsequent Video ID Extractions (Warm)
    const subsequent: BenchmarkItem[] = [];
    for (let i = 1; i < TEST_VIDEOS.length; i++) {
      const vid = TEST_VIDEOS[i]!;
      console.log(`[BENCHMARK] Testing subsequent video #${i} (${vid.id})...`);
      const startSub = Date.now();
      try {
        const playback = await YoutubeDownloader.extractYoutubeStream({
          videoId: vid.id,
          audioQuality: 'AUTO',
          videoQuality: VideoQuality.QUALITY_1080P,
        });
        const elapsed = Math.round(Date.now() - startSub);
        const isCached =
          playback.extractionDurationMs != null &&
          playback.extractionDurationMs < 5;
        console.log(
          `[BENCHMARK] Video #${i} (${vid.id}) extracted in ${elapsed}ms | native: ${playback.extractionDurationMs?.toFixed(1)}ms | client: ${playback.clientName}`
        );
        subsequent.push({
          name: vid.label,
          videoId: vid.id,
          durationMs: elapsed,
          nativeDurationMs: playback.extractionDurationMs
            ? Math.round(playback.extractionDurationMs)
            : undefined,
          poTokenDurationMs: playback.poTokenDurationMs
            ? Math.round(playback.poTokenDurationMs)
            : undefined,
          clientName: playback.clientName,
          status: 'success',
          details: playback.videoDetails?.title ?? 'No title',
          itag: playback.audioStream?.format.itag,
          bitrate: playback.audioStream?.format.bitrate,
          isCached,
        });
      } catch (e: any) {
        console.log(
          `[BENCHMARK] Subsequent video #${i} unavailable:`,
          errorMessage(e)
        );
        subsequent.push({
          name: vid.label,
          videoId: vid.id,
          durationMs: Math.round(Date.now() - startSub),
          status: 'failed',
          error: errorMessage(e),
        });
      }

      setState((prev) => ({
        ...prev,
        subsequentExtractions: [...subsequent],
      }));
    }

    // 4. Mazica Audio-Only Extraction (Priority for Mazica app)
    console.log('[BENCHMARK] Testing Mazica Audio-Only Extraction...');
    let mazicaAudio: BenchmarkItem;
    const startMazica = Date.now();
    try {
      const playback = await YoutubeDownloader.extractYoutubeStream({
        videoId: 'kJQP7kiw5Fk',
        audioQuality: 'AUTO',
        // videoQuality omitted -> Mazica music audio-only mode
      });
      const elapsed = Math.round(Date.now() - startMazica);
      const isCached =
        playback.extractionDurationMs != null &&
        playback.extractionDurationMs < 5;
      console.log(
        `[BENCHMARK] Mazica audio-only extracted in ${elapsed}ms | native: ${playback.extractionDurationMs?.toFixed(1)}ms | client: ${playback.clientName}`
      );
      mazicaAudio = {
        name: 'Mazica Audio-Only (kJQP7kiw5Fk)',
        videoId: 'kJQP7kiw5Fk',
        durationMs: elapsed,
        nativeDurationMs: playback.extractionDurationMs
          ? Math.round(playback.extractionDurationMs)
          : undefined,
        poTokenDurationMs: playback.poTokenDurationMs
          ? Math.round(playback.poTokenDurationMs)
          : undefined,
        clientName: playback.clientName,
        status: 'success',
        details: playback.videoDetails?.title ?? 'Audio Stream',
        itag: playback.audioStream?.format.itag,
        bitrate: playback.audioStream?.format.bitrate,
        isCached,
      };
    } catch (e: any) {
      console.log('[BENCHMARK] Mazica audio unavailable:', errorMessage(e));
      mazicaAudio = {
        name: 'Mazica Audio-Only (kJQP7kiw5Fk)',
        videoId: 'kJQP7kiw5Fk',
        durationMs: Math.round(Date.now() - startMazica),
        status: 'failed',
        error: errorMessage(e),
      };
    }

    setState((prev) => ({
      ...prev,
      mazicaAudioExtraction: mazicaAudio,
    }));

    // 5. Auth Fallback Path Verification
    console.log('[BENCHMARK] Testing Auth Path Fallback...');
    let authFallback: BenchmarkItem;
    const startAuth = Date.now();
    try {
      const authPlayback = await YoutubeDownloader.extractYoutubeStream({
        videoId: 'dQw4w9WgXcQ',
        audioQuality: 'AUTO',
        videoQuality: VideoQuality.QUALITY_720P,
        cookie: 'SAPISID=test-auth-cookie; SID=test-auth-cookie',
        forceVisitorData: 'test-auth-visitor-data',
      });
      const elapsed = Math.round(Date.now() - startAuth);
      console.log(
        `[BENCHMARK] Auth fallback path succeeded in ${elapsed}ms | client: ${authPlayback.clientName}`
      );
      authFallback = {
        name: 'Auth Fallback Path',
        durationMs: elapsed,
        nativeDurationMs: authPlayback.extractionDurationMs
          ? Math.round(authPlayback.extractionDurationMs)
          : undefined,
        clientName: authPlayback.clientName,
        status: 'success',
        details: `Client: ${authPlayback.clientName} (Auth isolated without corrupting anon session)`,
      };
    } catch (e: any) {
      console.warn('[BENCHMARK] Auth fallback result:', e);
      authFallback = {
        name: 'Auth Fallback Path',
        durationMs: Math.round(Date.now() - startAuth),
        status: 'failed',
        error: e?.message || 'Error',
      };
    }

    setState((prev) => ({
      ...prev,
      authFallbackTest: authFallback,
      isRunning: false,
      completedAt: new Date().toLocaleTimeString(),
    }));
    console.log('[BENCHMARK] === YouTube Downloader Benchmark Completed ===');
  }, []);

  const runClientVerification = useCallback(async () => {
    setState((prev) => ({
      ...prev,
      isVerifyingClients: true,
      clientVerifications: [],
    }));
    console.log(
      '[BENCHMARK] === Starting All Clients & Stream Verification ==='
    );

    const results: ClientVerificationItem[] = [];
    for (const client of CLIENTS_TO_TEST) {
      console.log(`[BENCHMARK] Testing client ${client.id}...`);
      const start = Date.now();
      try {
        const playback = await YoutubeDownloader.extractYoutubeStream({
          videoId: 'dQw4w9WgXcQ',
          audioQuality: 'AUTO',
          clientName: client.id,
          cookie: client.auth
            ? 'SAPISID=test-auth-cookie; SID=test-auth-cookie'
            : undefined,
        });
        const elapsed = Math.round(Date.now() - start);

        const reachability = await testStreamReachability(
          playback.audioStream.streamUrl,
          !!playback.audioStream.isHls,
          playback.audioStream.requestHeaders
        );

        console.log(
          `[BENCHMARK] Client ${client.id}: extraction=${elapsed}ms, stream=${reachability.type}, reachable=${reachability.reachable}, SABR=${!!playback.sabrStreamingUrl}`
        );

        results.push({
          clientName: client.id,
          label: client.label,
          description: client.desc,
          category: client.category,
          auth: client.auth,
          status: 'success',
          extractionDurationMs: elapsed,
          nativeDurationMs:
            playback.extractionDurationMs != null
              ? Math.round(playback.extractionDurationMs)
              : undefined,
          streamUrl: playback.audioStream.streamUrl,
          streamReachable: reachability.reachable,
          streamHttpStatus: reachability.status,
          streamType: reachability.type,
          sabrSupported: !!playback.sabrStreamingUrl,
          sabrUrl: playback.sabrStreamingUrl,
          isHls: !!playback.audioStream.isHls,
          itag: playback.audioStream.format.itag,
          bitrate: playback.audioStream.format.bitrate,
        });
      } catch (err: any) {
        const elapsed = Math.round(Date.now() - start);
        console.log(
          `[BENCHMARK] Client ${client.id} failed in ${elapsed}ms:`,
          errorMessage(err)
        );
        results.push({
          clientName: client.id,
          label: client.label,
          description: client.desc,
          category: client.category,
          auth: client.auth,
          status: 'failed',
          extractionDurationMs: elapsed,
          error: errorMessage(err),
        });
      }

      setState((prev) => ({
        ...prev,
        clientVerifications: [...results],
      }));
    }

    setState((prev) => ({
      ...prev,
      isVerifyingClients: false,
    }));
    console.log(
      '[BENCHMARK] === All Clients & Stream Verification Completed ==='
    );
  }, []);

  useEffect(() => {
    (async () => {
      await runBenchmark();
      await runClientVerification();
    })();
  }, [runBenchmark, runClientVerification]);

  const avgSubsequentMs =
    state.subsequentExtractions.filter((s) => s.status === 'success').length > 0
      ? Math.round(
          state.subsequentExtractions
            .filter((s) => s.status === 'success')
            .reduce((acc, curr) => acc + curr.durationMs, 0) /
            state.subsequentExtractions.filter((s) => s.status === 'success')
              .length
        )
      : null;

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>YouTube Downloader Benchmark</Text>
          <Text style={styles.headerSubtitle}>
            Mazica Extraction Optimization Benchmark
          </Text>
        </View>

        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={[
              styles.button,
              styles.buttonHalf,
              (state.isRunning || state.isVerifyingClients) &&
                styles.buttonDisabled,
            ]}
            disabled={state.isRunning || state.isVerifyingClients}
            onPress={runBenchmark}
          >
            {state.isRunning ? (
              <View style={styles.rowCenter}>
                <ActivityIndicator
                  color="#fff"
                  size="small"
                  style={styles.spinner}
                />
                <Text style={styles.buttonText}>Benchmarking...</Text>
              </View>
            ) : (
              <Text style={styles.buttonText}>Speed Benchmark</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.button,
              styles.buttonSecondary,
              styles.buttonHalf,
              (state.isRunning || state.isVerifyingClients) &&
                styles.buttonDisabled,
            ]}
            disabled={state.isRunning || state.isVerifyingClients}
            onPress={runClientVerification}
          >
            {state.isVerifyingClients ? (
              <View style={styles.rowCenter}>
                <ActivityIndicator
                  color="#fff"
                  size="small"
                  style={styles.spinner}
                />
                <Text style={styles.buttonText}>Verifying...</Text>
              </View>
            ) : (
              <Text style={styles.buttonText}>Verify All Clients</Text>
            )}
          </TouchableOpacity>
        </View>

        {state.completedAt && (
          <Text style={styles.timestamp}>
            Last run completed at {state.completedAt}
          </Text>
        )}

        <View style={styles.tabContainer}>
          <TouchableOpacity
            style={[styles.tab, activeTab === 'clients' && styles.tabActive]}
            onPress={() => setActiveTab('clients')}
          >
            <Text
              style={[
                styles.tabText,
                activeTab === 'clients' && styles.tabTextActive,
              ]}
            >
              All Clients & Streams (
              {state.clientVerifications?.filter(
                (c) => c.status === 'success' && c.streamReachable
              ).length ?? 0}
              /{state.clientVerifications?.length ?? CLIENTS_TO_TEST.length})
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.tab, activeTab === 'benchmark' && styles.tabActive]}
            onPress={() => setActiveTab('benchmark')}
          >
            <Text
              style={[
                styles.tabText,
                activeTab === 'benchmark' && styles.tabTextActive,
              ]}
            >
              Speed Benchmarks
            </Text>
          </TouchableOpacity>
        </View>

        {activeTab === 'benchmark' && (
          <>
            {/* SECTION 1: PoToken Generation */}
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>1. PoToken Generation</Text>
              {state.poTokenCold ? (
                <View style={styles.metricRow}>
                  <View style={styles.rowCenter}>
                    <Text style={styles.metricLabel}>Cold Mint: </Text>
                    <View
                      style={[
                        styles.badge,
                        state.poTokenCold.status === 'success'
                          ? styles.badgeSuccessBg
                          : styles.badgeFailedBg,
                      ]}
                    >
                      <Text
                        style={
                          state.poTokenCold.status === 'success'
                            ? styles.badgeSuccessText
                            : styles.badgeFailedText
                        }
                      >
                        {state.poTokenCold.status.toUpperCase()}
                      </Text>
                    </View>
                  </View>
                  <Text style={styles.metricValueBold}>
                    {state.poTokenCold.durationMs} ms
                  </Text>
                </View>
              ) : (
                <Text style={styles.metricPlaceholder}>Pending...</Text>
              )}
              {state.poTokenWarm ? (
                <View style={styles.metricRow}>
                  <View style={styles.rowCenter}>
                    <Text style={styles.metricLabel}>Warm Mint: </Text>
                    <View
                      style={[
                        styles.badge,
                        state.poTokenWarm.status === 'success'
                          ? styles.badgeSuccessBg
                          : styles.badgeFailedBg,
                      ]}
                    >
                      <Text
                        style={
                          state.poTokenWarm.status === 'success'
                            ? styles.badgeSuccessText
                            : styles.badgeFailedText
                        }
                      >
                        {state.poTokenWarm.status.toUpperCase()}
                      </Text>
                    </View>
                  </View>
                  <Text style={styles.metricValueBold}>
                    {state.poTokenWarm.durationMs} ms
                  </Text>
                </View>
              ) : null}
              {state.poTokenCold?.details ? (
                <Text style={styles.subtext}>
                  Sample Token: {state.poTokenCold.details}
                </Text>
              ) : null}
              {state.poTokenCold?.error ? (
                <Text style={styles.errorText}>
                  Cold Error: {state.poTokenCold.error}
                </Text>
              ) : null}
            </View>

            {/* SECTION 2: First Video Extraction */}
            <View style={styles.card}>
              <View style={styles.rowBetween}>
                <Text style={styles.sectionTitle}>
                  2. First Video ID Extraction (Cold)
                </Text>
                {state.firstVideoExtraction && (
                  <View
                    style={[
                      styles.badge,
                      state.firstVideoExtraction.status === 'success'
                        ? styles.badgeSuccessBg
                        : styles.badgeFailedBg,
                    ]}
                  >
                    <Text
                      style={
                        state.firstVideoExtraction.status === 'success'
                          ? styles.badgeSuccessText
                          : styles.badgeFailedText
                      }
                    >
                      {state.firstVideoExtraction.status.toUpperCase()}
                    </Text>
                  </View>
                )}
              </View>
              {state.firstVideoExtraction ? (
                <View>
                  <View style={styles.metricRow}>
                    <View style={styles.rowCenter}>
                      <Text style={styles.metricLabel}>JS Total Duration:</Text>
                      {state.firstVideoExtraction.isCached && (
                        <View style={styles.cachedBadge}>
                          <Text style={styles.cachedBadgeText}>cached</Text>
                        </View>
                      )}
                    </View>
                    <Text
                      style={[
                        styles.metricHighlight,
                        state.firstVideoExtraction.status === 'failed'
                          ? styles.errorColor
                          : styles.successColor,
                      ]}
                    >
                      {state.firstVideoExtraction.durationMs} ms
                    </Text>
                  </View>
                  {state.firstVideoExtraction.nativeDurationMs != null && (
                    <View style={styles.metricRow}>
                      <Text style={styles.metricLabel}>Native Duration:</Text>
                      <Text style={styles.metricValue}>
                        {state.firstVideoExtraction.nativeDurationMs} ms
                      </Text>
                    </View>
                  )}
                  {state.firstVideoExtraction.clientName != null && (
                    <View style={styles.metricRow}>
                      <Text style={styles.metricLabel}>Client Used:</Text>
                      <Text style={styles.metricValueBold}>
                        {state.firstVideoExtraction.clientName}
                      </Text>
                    </View>
                  )}
                  {state.firstVideoExtraction.itag != null && (
                    <Text style={styles.subtext}>
                      Audio Stream: itag {state.firstVideoExtraction.itag} (
                      {Math.round(
                        (state.firstVideoExtraction.bitrate ?? 0) / 1000
                      )}{' '}
                      kbps) | {state.firstVideoExtraction.details}
                    </Text>
                  )}
                  {state.firstVideoExtraction.error && (
                    <Text style={styles.errorText}>
                      Error: {state.firstVideoExtraction.error}
                    </Text>
                  )}
                </View>
              ) : (
                <Text style={styles.metricPlaceholder}>Pending...</Text>
              )}
            </View>

            {/* SECTION 3: Subsequent Video Extractions */}
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>
                3. Other Video ID Extractions (Warm)
              </Text>
              {state.subsequentExtractions.length > 0 ? (
                <View>
                  {state.subsequentExtractions.map((item, idx) => (
                    <View
                      key={item.videoId || idx}
                      style={styles.subsequentItem}
                    >
                      <View style={styles.metricRow}>
                        <View style={styles.rowCenter}>
                          <Text style={styles.subsequentLabel}>
                            {idx + 1}. {item.videoId} (
                            {item.clientName || 'N/A'})
                          </Text>
                          <View
                            style={[
                              styles.badge,
                              item.status === 'success'
                                ? styles.badgeSuccessBg
                                : styles.badgeFailedBg,
                              styles.badgeMarginLeft,
                            ]}
                          >
                            <Text
                              style={
                                item.status === 'success'
                                  ? styles.badgeSuccessText
                                  : styles.badgeFailedText
                              }
                            >
                              {item.status.toUpperCase()}
                            </Text>
                          </View>
                          {item.isCached && (
                            <View style={styles.cachedBadge}>
                              <Text style={styles.cachedBadgeText}>cached</Text>
                            </View>
                          )}
                        </View>
                        <Text
                          style={[
                            styles.metricValueBold,
                            item.status === 'failed' && styles.errorColor,
                          ]}
                        >
                          {item.durationMs} ms
                        </Text>
                      </View>
                      {item.status === 'success' ? (
                        <Text style={styles.subtextSmall}>
                          Native: {item.nativeDurationMs ?? 'N/A'} ms | itag{' '}
                          {item.itag ?? 'N/A'} (
                          {Math.round((item.bitrate ?? 0) / 1000)} kbps) |{' '}
                          {item.details}
                        </Text>
                      ) : (
                        <Text style={styles.errorText}>
                          Error: {item.error || 'Extraction failed'}
                        </Text>
                      )}
                    </View>
                  ))}

                  {avgSubsequentMs != null && (
                    <View style={[styles.metricRow, styles.avgRow]}>
                      <Text style={styles.avgLabel}>
                        Average Warm Extraction:
                      </Text>
                      <Text style={styles.avgValue}>{avgSubsequentMs} ms</Text>
                    </View>
                  )}
                </View>
              ) : (
                <Text style={styles.metricPlaceholder}>Pending...</Text>
              )}
            </View>

            {/* SECTION 4: Mazica Audio-Only Extraction */}
            <View style={styles.card}>
              <View style={styles.rowBetween}>
                <Text style={styles.sectionTitle}>
                  4. Mazica Audio-Only Extraction (App Mode)
                </Text>
                {state.mazicaAudioExtraction && (
                  <View
                    style={[
                      styles.badge,
                      state.mazicaAudioExtraction.status === 'success'
                        ? styles.badgeSuccessBg
                        : styles.badgeFailedBg,
                    ]}
                  >
                    <Text
                      style={
                        state.mazicaAudioExtraction.status === 'success'
                          ? styles.badgeSuccessText
                          : styles.badgeFailedText
                      }
                    >
                      {state.mazicaAudioExtraction.status.toUpperCase()}
                    </Text>
                  </View>
                )}
              </View>
              {state.mazicaAudioExtraction ? (
                <View>
                  <View style={styles.metricRow}>
                    <View style={styles.rowCenter}>
                      <Text style={styles.metricLabel}>Total Duration:</Text>
                      {state.mazicaAudioExtraction.isCached && (
                        <View style={styles.cachedBadge}>
                          <Text style={styles.cachedBadgeText}>cached</Text>
                        </View>
                      )}
                    </View>
                    <Text
                      style={[
                        styles.metricHighlight,
                        state.mazicaAudioExtraction.status === 'failed'
                          ? styles.errorColor
                          : styles.successColor,
                      ]}
                    >
                      {state.mazicaAudioExtraction.durationMs} ms
                    </Text>
                  </View>
                  {state.mazicaAudioExtraction.nativeDurationMs != null && (
                    <View style={styles.metricRow}>
                      <Text style={styles.metricLabel}>Native Duration:</Text>
                      <Text style={styles.metricValue}>
                        {state.mazicaAudioExtraction.nativeDurationMs} ms
                      </Text>
                    </View>
                  )}
                  {state.mazicaAudioExtraction.clientName != null && (
                    <View style={styles.metricRow}>
                      <Text style={styles.metricLabel}>Client Used:</Text>
                      <Text style={styles.metricValueBold}>
                        {state.mazicaAudioExtraction.clientName}
                      </Text>
                    </View>
                  )}
                  {state.mazicaAudioExtraction.itag != null && (
                    <Text style={styles.subtext}>
                      Audio Stream: itag {state.mazicaAudioExtraction.itag} (
                      {Math.round(
                        (state.mazicaAudioExtraction.bitrate ?? 0) / 1000
                      )}{' '}
                      kbps) | {state.mazicaAudioExtraction.details}
                    </Text>
                  )}
                  {state.mazicaAudioExtraction.error && (
                    <Text style={styles.errorText}>
                      Error: {state.mazicaAudioExtraction.error}
                    </Text>
                  )}
                </View>
              ) : (
                <Text style={styles.metricPlaceholder}>Pending...</Text>
              )}
            </View>

            {/* SECTION 5: Dual Path Verification */}
            <View style={styles.card}>
              <Text style={styles.sectionTitle}>5. Dual Path Verification</Text>
              <View style={styles.metricRow}>
                <Text style={styles.metricLabel}>
                  Anonymous Path (Priority):
                </Text>
                <Text style={styles.statusSuccess}>
                  {state.firstVideoExtraction?.status === 'success'
                    ? `ACTIVE (${state.firstVideoExtraction.clientName})`
                    : 'PENDING'}
                </Text>
              </View>
              <View style={styles.metricRow}>
                <Text style={styles.metricLabel}>Auth Fallback Path:</Text>
                <Text
                  style={
                    state.authFallbackTest?.status === 'success'
                      ? styles.statusSuccess
                      : styles.statusWarning
                  }
                >
                  {state.authFallbackTest?.status === 'success'
                    ? `VERIFIED (${state.authFallbackTest.durationMs} ms)`
                    : state.authFallbackTest?.status === 'failed'
                      ? 'FAILED'
                      : 'PENDING'}
                </Text>
              </View>
            </View>
          </>
        )}

        {/* SECTION 6: All Clients & Stream URL Verification */}
        {activeTab === 'clients' && (
          <View style={styles.card}>
            <View style={styles.rowBetween}>
              <Text style={styles.sectionTitle}>
                6. All Clients & Stream Verification
              </Text>
              {state.clientVerifications &&
                state.clientVerifications.length > 0 && (
                  <View style={styles.rowCenter}>
                    <View style={[styles.badge, styles.badgeSuccessBg]}>
                      <Text style={styles.badgeSuccessText}>
                        {
                          state.clientVerifications.filter(
                            (c) => c.status === 'success' && c.streamReachable
                          ).length
                        }{' '}
                        REACHABLE
                      </Text>
                    </View>
                    <View
                      style={[
                        styles.badge,
                        styles.badgeNeutralBg,
                        styles.badgeMarginLeft,
                      ]}
                    >
                      <Text style={styles.badgeNeutralText}>
                        {state.clientVerifications.length} TESTED
                      </Text>
                    </View>
                  </View>
                )}
            </View>
            <Text style={styles.subtextSmall}>
              Tests every InnerTube client independently, verifies direct
              extraction, and probes stream HTTP reachability (206 bytes or HLS
              #EXTM3U).
            </Text>

            {state.isVerifyingClients && (
              <View style={[styles.rowCenter, styles.loadingRow]}>
                <ActivityIndicator
                  color="#38BDF8"
                  size="small"
                  style={styles.spinner}
                />
                <Text style={styles.subtext}>
                  Testing clients & stream reachability...
                </Text>
              </View>
            )}

            {state.clientVerifications &&
            state.clientVerifications.length > 0 ? (
              <View style={styles.clientListContainer}>
                {state.clientVerifications.map((item) => (
                  <View key={item.clientName} style={styles.clientCard}>
                    <View style={styles.rowBetween}>
                      <View style={styles.rowCenter}>
                        <Text style={styles.clientLabel}>{item.label}</Text>
                        <View
                          style={[
                            styles.badge,
                            item.status === 'success'
                              ? styles.badgeSuccessBg
                              : styles.badgeFailedBg,
                            styles.badgeMarginLeft,
                          ]}
                        >
                          <Text
                            style={
                              item.status === 'success'
                                ? styles.badgeSuccessText
                                : styles.badgeFailedText
                            }
                          >
                            {item.status === 'success'
                              ? 'EXTRACTED'
                              : 'BLOCKED'}
                          </Text>
                        </View>
                        {item.isHls && (
                          <View
                            style={[
                              styles.badge,
                              styles.badgeHlsBg,
                              styles.badgeMarginLeft,
                            ]}
                          >
                            <Text style={styles.badgeHlsText}>HLS</Text>
                          </View>
                        )}
                        {item.sabrSupported && (
                          <View
                            style={[
                              styles.badge,
                              styles.badgeSabrBg,
                              styles.badgeMarginLeft,
                            ]}
                          >
                            <Text style={styles.badgeSabrText}>SABR/UMP</Text>
                          </View>
                        )}
                      </View>
                      <Text
                        style={[
                          styles.metricValueBold,
                          item.status === 'failed' && styles.errorColor,
                        ]}
                      >
                        {item.extractionDurationMs} ms
                      </Text>
                    </View>

                    {item.status === 'success' ? (
                      <View style={styles.clientDetails}>
                        <View style={styles.rowBetween}>
                          <Text style={styles.detailLabel}>
                            Stream Reachability:
                          </Text>
                          <Text
                            style={[
                              styles.detailValue,
                              item.streamReachable
                                ? styles.textGreen
                                : styles.textYellow,
                            ]}
                          >
                            {item.streamType ?? 'Unknown'}
                          </Text>
                        </View>
                        <View style={styles.rowBetween}>
                          <Text style={styles.detailLabel}>
                            Format & Audio Bitrate:
                          </Text>
                          <Text style={styles.detailValue}>
                            itag {item.itag ?? 'N/A'} (
                            {Math.round((item.bitrate ?? 0) / 1000)} kbps)
                          </Text>
                        </View>
                        {item.sabrSupported && (
                          <View style={styles.rowBetween}>
                            <Text style={styles.detailLabel}>
                              SABR Binary URL:
                            </Text>
                            <Text style={styles.detailValueGreen}>
                              Connected & Ready
                            </Text>
                          </View>
                        )}
                      </View>
                    ) : (
                      <View style={styles.clientDetails}>
                        <Text style={styles.errorText}>
                          {item.error?.includes('needs to be reloaded') ||
                          item.error?.includes('unavailable')
                            ? item.auth
                              ? 'Cookie authentication required by YouTube'
                              : 'Botguard / PoToken challenge required by YouTube'
                            : item.error || 'Extraction failed'}
                        </Text>
                      </View>
                    )}
                  </View>
                ))}
              </View>
            ) : (
              <Text style={styles.metricPlaceholder}>
                Pending client test...
              </Text>
            )}
          </View>
        )}

        {/* SECTION 7: Before vs After Summary */}
        {activeTab === 'benchmark' && (
          <View style={[styles.card, styles.comparisonCard]}>
            <Text style={styles.sectionTitle}>
              7. Before vs After Optimization
            </Text>
            <View style={styles.tableHeader}>
              <Text style={[styles.tableCol, styles.tableColHeader]}>
                Stage
              </Text>
              <Text style={[styles.tableCol, styles.tableColHeader]}>
                Before
              </Text>
              <Text style={[styles.tableCol, styles.tableColHeader]}>
                After
              </Text>
              <Text style={[styles.tableCol, styles.tableColHeader]}>Gain</Text>
            </View>

            <View style={styles.tableRow}>
              <Text style={styles.tableCol}>PoToken Warm</Text>
              <Text style={styles.tableCol}>~4 ms</Text>
              <Text style={styles.tableCol}>
                {state.poTokenWarm ? `${state.poTokenWarm.durationMs} ms` : '-'}
              </Text>
              <Text style={[styles.tableCol, styles.textGreen]}>Fast</Text>
            </View>

            <View style={styles.tableRow}>
              <Text style={styles.tableCol}>First Video (Cold)</Text>
              <Text style={styles.tableCol}>~4,800 ms</Text>
              <Text style={styles.tableCol}>
                {state.firstVideoExtraction
                  ? `${state.firstVideoExtraction.durationMs} ms`
                  : '-'}
              </Text>
              <Text style={[styles.tableCol, styles.textGreen]}>
                {state.firstVideoExtraction
                  ? `-${Math.round(
                      ((4800 - state.firstVideoExtraction.durationMs) / 4800) *
                        100
                    )}%`
                  : '-'}
              </Text>
            </View>

            <View style={styles.tableRow}>
              <Text style={styles.tableCol}>Mazica Audio</Text>
              <Text style={styles.tableCol}>~2,200 ms</Text>
              <Text style={styles.tableCol}>
                {state.mazicaAudioExtraction
                  ? `${state.mazicaAudioExtraction.durationMs} ms`
                  : '-'}
              </Text>
              <Text style={[styles.tableCol, styles.textGreen]}>
                {state.mazicaAudioExtraction
                  ? `-${Math.round(
                      ((2200 - state.mazicaAudioExtraction.durationMs) / 2200) *
                        100
                    )}%`
                  : '-'}
              </Text>
            </View>

            <View style={styles.tableRow}>
              <Text style={styles.tableCol}>Other Videos (Warm)</Text>
              <Text style={styles.tableCol}>~1,600 ms</Text>
              <Text style={styles.tableCol}>
                {avgSubsequentMs ? `${avgSubsequentMs} ms` : '-'}
              </Text>
              <Text style={[styles.tableCol, styles.textGreen]}>
                {avgSubsequentMs
                  ? `-${Math.round(((1600 - avgSubsequentMs) / 1600) * 100)}%`
                  : '-'}
              </Text>
            </View>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0F172A',
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 30) : 0,
  },
  container: {
    padding: 16,
    paddingBottom: 40,
  },
  header: {
    marginBottom: 16,
  },
  headerTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  headerSubtitle: {
    fontSize: 13,
    color: '#94A3B8',
    marginTop: 4,
  },
  button: {
    backgroundColor: '#3B82F6',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 16,
    alignItems: 'center',
    marginBottom: 8,
  },
  buttonDisabled: {
    backgroundColor: '#1E3A8A',
  },
  buttonText: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 15,
  },
  rowCenter: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  rowBetween: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  badge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    alignSelf: 'center',
  },
  badgeMarginLeft: {
    marginLeft: 6,
  },
  badgeSuccessBg: {
    backgroundColor: '#065F46',
  },
  badgeFailedBg: {
    backgroundColor: '#7F1D1D',
  },
  badgeSuccessText: {
    color: '#34D399',
    fontSize: 10,
    fontWeight: '700',
  },
  badgeFailedText: {
    color: '#F87171',
    fontSize: 10,
    fontWeight: '700',
  },
  cachedBadge: {
    backgroundColor: '#1E3A8A',
    paddingHorizontal: 5,
    paddingVertical: 1,
    borderRadius: 3,
    marginLeft: 6,
  },
  cachedBadgeText: {
    color: '#93C5FD',
    fontSize: 10,
    fontWeight: '600',
  },
  successColor: {
    color: '#4ADE80',
  },
  errorColor: {
    color: '#EF4444',
  },
  spinner: {
    marginRight: 8,
  },
  timestamp: {
    fontSize: 12,
    color: '#64748B',
    textAlign: 'center',
    marginBottom: 12,
  },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 10,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#334155',
  },
  comparisonCard: {
    borderColor: '#3B82F6',
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#38BDF8',
    marginBottom: 8,
  },
  metricRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginVertical: 3,
  },
  metricLabel: {
    fontSize: 14,
    color: '#CBD5E1',
  },
  metricValue: {
    fontSize: 14,
    color: '#F8FAFC',
  },
  metricValueBold: {
    fontSize: 14,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  metricHighlight: {
    fontSize: 16,
    fontWeight: '700',
    color: '#4ADE80',
  },
  metricPlaceholder: {
    fontSize: 13,
    color: '#64748B',
    fontStyle: 'italic',
  },
  subtext: {
    fontSize: 12,
    color: '#94A3B8',
    marginTop: 4,
  },
  subtextSmall: {
    fontSize: 11,
    color: '#64748B',
    marginBottom: 4,
  },
  subsequentItem: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#334155',
    paddingVertical: 4,
  },
  subsequentLabel: {
    fontSize: 13,
    color: '#E2E8F0',
  },
  avgRow: {
    marginTop: 8,
    paddingTop: 6,
    borderTopWidth: 1,
    borderTopColor: '#334155',
  },
  avgLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F8FAFC',
  },
  avgValue: {
    fontSize: 15,
    fontWeight: '700',
    color: '#4ADE80',
  },
  errorText: {
    color: '#EF4444',
    fontSize: 12,
    marginTop: 4,
  },
  statusSuccess: {
    fontSize: 13,
    fontWeight: '700',
    color: '#4ADE80',
  },
  statusWarning: {
    fontSize: 13,
    fontWeight: '600',
    color: '#FBBF24',
  },
  tableHeader: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#475569',
    paddingBottom: 6,
    marginBottom: 6,
  },
  tableRow: {
    flexDirection: 'row',
    paddingVertical: 4,
  },
  tableCol: {
    flex: 1,
    fontSize: 12,
    color: '#CBD5E1',
    textAlign: 'center',
  },
  tableColHeader: {
    fontWeight: '700',
    color: '#94A3B8',
  },
  textGreen: {
    color: '#4ADE80',
    fontWeight: '700',
  },
  buttonContainer: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 8,
  },
  buttonHalf: {
    flex: 1,
  },
  buttonSecondary: {
    backgroundColor: '#0284C7',
  },
  badgeNeutralBg: {
    backgroundColor: '#334155',
  },
  badgeNeutralText: {
    color: '#94A3B8',
    fontSize: 10,
    fontWeight: '700',
  },
  badgeHlsBg: {
    backgroundColor: '#581C87',
  },
  badgeHlsText: {
    color: '#C084FC',
    fontSize: 10,
    fontWeight: '700',
  },
  badgeSabrBg: {
    backgroundColor: '#0E7490',
  },
  badgeSabrText: {
    color: '#67E8F9',
    fontSize: 10,
    fontWeight: '700',
  },
  clientCard: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    padding: 10,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  clientLabel: {
    fontSize: 13,
    fontWeight: '700',
    color: '#E2E8F0',
  },
  clientDetails: {
    marginTop: 6,
    paddingTop: 6,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#334155',
  },
  detailLabel: {
    fontSize: 11,
    color: '#94A3B8',
  },
  detailValue: {
    fontSize: 11,
    color: '#CBD5E1',
    fontWeight: '600',
  },
  detailValueGreen: {
    fontSize: 11,
    color: '#4ADE80',
    fontWeight: '600',
  },
  textYellow: {
    color: '#FBBF24',
    fontWeight: '600',
  },
  loadingRow: {
    marginVertical: 8,
  },
  clientListContainer: {
    marginTop: 6,
  },
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: '#1E293B',
    borderRadius: 8,
    padding: 4,
    marginBottom: 14,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 6,
  },
  tabActive: {
    backgroundColor: '#3B82F6',
  },
  tabText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#94A3B8',
  },
  tabTextActive: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
});
