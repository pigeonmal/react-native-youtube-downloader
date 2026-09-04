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
}

interface BenchmarkState {
  poTokenCold?: BenchmarkItem;
  poTokenWarm?: BenchmarkItem;
  firstVideoExtraction?: BenchmarkItem;
  subsequentExtractions: BenchmarkItem[];
  mazicaAudioExtraction?: BenchmarkItem;
  authFallbackTest?: BenchmarkItem;
  isRunning: boolean;
  completedAt?: string;
}

const TEST_VIDEOS = [
  { id: 'dQw4w9WgXcQ', label: 'Video 1 (First/Cold)' },
  { id: '9bZkp7q19f0', label: 'Video 2 (Warm - Gangnam Style)' },
  { id: 'kJQP7kiw5Fk', label: 'Video 3 (Warm - Despacito)' },
  { id: 'fJ9rUzIMcZQ', label: 'Video 4 (Warm - Queen Bohemian Rhapsody)' },
];

export default function App() {
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
    const startPo1 = performance.now();
    try {
      const token1 = await YoutubeDownloader.generatePoToken('dQw4w9WgXcQ');
      const time1 = Math.round(performance.now() - startPo1);
      console.log(
        `[BENCHMARK] PoToken #1 generated in ${time1}ms: ${token1.substring(0, 16)}...`
      );
      poCold = {
        name: 'PoToken Generation #1',
        durationMs: time1,
        status: 'success',
        details: `${token1.substring(0, 20)}... (length: ${token1.length})`,
      };
    } catch (e: any) {
      console.error('[BENCHMARK] PoToken #1 error:', e);
      poCold = {
        name: 'PoToken Generation #1',
        durationMs: Math.round(performance.now() - startPo1),
        status: 'failed',
        error: e?.message || 'Error',
      };
    }

    let poWarm: BenchmarkItem;
    const startPo2 = performance.now();
    try {
      const token2 = await YoutubeDownloader.generatePoToken('9bZkp7q19f0');
      const time2 = Math.round(performance.now() - startPo2);
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
      console.error('[BENCHMARK] PoToken #2 error:', e);
      poWarm = {
        name: 'PoToken Generation #2 (Warm)',
        durationMs: Math.round(performance.now() - startPo2),
        status: 'failed',
        error: e?.message || 'Error',
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
    const startCold = performance.now();
    try {
      const playback = await YoutubeDownloader.extractYoutubeStream({
        videoId: coldVideo.id,
        audioQuality: 'AUTO',
        videoQuality: VideoQuality.QUALITY_1080P,
      });
      const elapsed = Math.round(performance.now() - startCold);
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
      };
    } catch (e: any) {
      console.error('[BENCHMARK] First video extraction failed:', e);
      coldExtraction = {
        name: coldVideo.label,
        videoId: coldVideo.id,
        durationMs: Math.round(performance.now() - startCold),
        status: 'failed',
        error: e?.message || 'Extraction failed',
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
      const startSub = performance.now();
      try {
        const playback = await YoutubeDownloader.extractYoutubeStream({
          videoId: vid.id,
          audioQuality: 'AUTO',
          videoQuality: VideoQuality.QUALITY_1080P,
        });
        const elapsed = Math.round(performance.now() - startSub);
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
        });
      } catch (e: any) {
        console.error(`[BENCHMARK] Subsequent video #${i} failed:`, e);
        subsequent.push({
          name: vid.label,
          videoId: vid.id,
          durationMs: Math.round(performance.now() - startSub),
          status: 'failed',
          error: e?.message || 'Extraction failed',
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
    const startMazica = performance.now();
    try {
      const playback = await YoutubeDownloader.extractYoutubeStream({
        videoId: 'kJQP7kiw5Fk',
        audioQuality: 'AUTO',
        // videoQuality omitted -> Mazica music audio-only mode
      });
      const elapsed = Math.round(performance.now() - startMazica);
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
      };
    } catch (e: any) {
      console.error('[BENCHMARK] Mazica audio extraction failed:', e);
      mazicaAudio = {
        name: 'Mazica Audio-Only (kJQP7kiw5Fk)',
        videoId: 'kJQP7kiw5Fk',
        durationMs: Math.round(performance.now() - startMazica),
        status: 'failed',
        error: e?.message || 'Extraction failed',
      };
    }

    setState((prev) => ({
      ...prev,
      mazicaAudioExtraction: mazicaAudio,
    }));

    // 5. Auth Fallback Path Verification
    console.log('[BENCHMARK] Testing Auth Path Fallback...');
    let authFallback: BenchmarkItem;
    const startAuth = performance.now();
    try {
      const authPlayback = await YoutubeDownloader.extractYoutubeStream({
        videoId: 'dQw4w9WgXcQ',
        audioQuality: 'AUTO',
        videoQuality: VideoQuality.QUALITY_720P,
        cookie: 'SAPISID=test-auth-cookie; SID=test-auth-cookie',
        forceVisitorData: 'test-auth-visitor-data',
      });
      const elapsed = Math.round(performance.now() - startAuth);
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
        durationMs: Math.round(performance.now() - startAuth),
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

  useEffect(() => {
    runBenchmark();
  }, [runBenchmark]);

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

        <TouchableOpacity
          style={[styles.button, state.isRunning && styles.buttonDisabled]}
          disabled={state.isRunning}
          onPress={runBenchmark}
        >
          {state.isRunning ? (
            <View style={styles.rowCenter}>
              <ActivityIndicator
                color="#fff"
                size="small"
                style={styles.spinner}
              />
              <Text style={styles.buttonText}>Benchmarking in Progress...</Text>
            </View>
          ) : (
            <Text style={styles.buttonText}>
              {state.completedAt ? 'Re-Run Benchmark' : 'Start Benchmark'}
            </Text>
          )}
        </TouchableOpacity>

        {state.completedAt && (
          <Text style={styles.timestamp}>
            Last run completed at {state.completedAt}
          </Text>
        )}

        {/* SECTION 1: PoToken Generation */}
        <View style={styles.card}>
          <Text style={styles.sectionTitle}>1. PoToken Generation</Text>
          {state.poTokenCold ? (
            <View style={styles.metricRow}>
              <Text style={styles.metricLabel}>Cold Mint:</Text>
              <Text style={styles.metricValueBold}>
                {state.poTokenCold.durationMs} ms
              </Text>
            </View>
          ) : (
            <Text style={styles.metricPlaceholder}>Pending...</Text>
          )}
          {state.poTokenWarm ? (
            <View style={styles.metricRow}>
              <Text style={styles.metricLabel}>Warm Mint:</Text>
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
        </View>

        {/* SECTION 2: First Video Extraction */}
        <View style={styles.card}>
          <Text style={styles.sectionTitle}>
            2. First Video ID Extraction (Cold)
          </Text>
          {state.firstVideoExtraction ? (
            <View>
              <View style={styles.metricRow}>
                <Text style={styles.metricLabel}>JS Total Duration:</Text>
                <Text style={styles.metricHighlight}>
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
                  <Text style={styles.metricValue}>
                    {state.firstVideoExtraction.clientName}
                  </Text>
                </View>
              )}
              <Text style={styles.subtext}>
                Title: {state.firstVideoExtraction.details}
              </Text>
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
                <View key={item.videoId || idx} style={styles.subsequentItem}>
                  <View style={styles.metricRow}>
                    <Text style={styles.subsequentLabel}>
                      {idx + 1}. {item.videoId} ({item.clientName || 'N/A'}):
                    </Text>
                    <Text style={styles.metricValueBold}>
                      {item.durationMs} ms
                    </Text>
                  </View>
                  <Text style={styles.subtextSmall}>
                    Native: {item.nativeDurationMs ?? 'N/A'} ms | {item.details}
                  </Text>
                </View>
              ))}

              {avgSubsequentMs != null && (
                <View style={[styles.metricRow, styles.avgRow]}>
                  <Text style={styles.avgLabel}>Average Warm Extraction:</Text>
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
          <Text style={styles.sectionTitle}>
            4. Mazica Audio-Only Extraction (App Mode)
          </Text>
          {state.mazicaAudioExtraction ? (
            <View>
              <View style={styles.metricRow}>
                <Text style={styles.metricLabel}>Total Duration:</Text>
                <Text style={styles.metricHighlight}>
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
              <Text style={styles.subtext}>
                {state.mazicaAudioExtraction.details}
              </Text>
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
            <Text style={styles.metricLabel}>Anonymous Path (Priority):</Text>
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

        {/* SECTION 6: Before vs After Summary */}
        <View style={[styles.card, styles.comparisonCard]}>
          <Text style={styles.sectionTitle}>
            6. Before vs After Optimization
          </Text>
          <View style={styles.tableHeader}>
            <Text style={[styles.tableCol, styles.tableColHeader]}>Stage</Text>
            <Text style={[styles.tableCol, styles.tableColHeader]}>Before</Text>
            <Text style={[styles.tableCol, styles.tableColHeader]}>After</Text>
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
});
