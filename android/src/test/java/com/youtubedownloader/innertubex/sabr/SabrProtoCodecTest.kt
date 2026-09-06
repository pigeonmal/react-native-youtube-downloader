package com.youtubedownloader.innertubex.sabr

import com.youtubedownloader.innertubex.client.IosClient
import com.youtubedownloader.innertubex.extractor.PlayerResponseParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SabrProtoCodecTest {

    @Test
    fun testProtoWireVarintRoundTrip() {
        val writer = ProtoWriter()
        writer.writeVarint(0L)
        writer.writeVarint(127L)
        writer.writeVarint(128L)
        writer.writeVarint(300L)
        writer.writeVarint(1000000L)

        val reader = ProtoReader(writer.toByteArray())
        assertEquals(0L, reader.readVarint())
        assertEquals(127L, reader.readVarint())
        assertEquals(128L, reader.readVarint())
        assertEquals(300L, reader.readVarint())
        assertEquals(1000000L, reader.readVarint())
        assertFalse(reader.hasMore())
    }

    @Test
    fun testProtoWireFieldsRoundTrip() {
        val writer = ProtoWriter()
            .writeInt32(1, 140)
            .writeString(2, "test_video_id")
            .writeBool(3, true)
            .writeBytes(4, byteArrayOf(0x01, 0x02, 0x03))

        val reader = ProtoReader(writer.toByteArray())

        val (tag1, type1) = reader.readTag()!!
        assertEquals(1, tag1)
        assertEquals(ProtoWriter.WIRE_TYPE_VARINT, type1)
        assertEquals(140L, reader.readVarint())

        val (tag2, type2) = reader.readTag()!!
        assertEquals(2, tag2)
        assertEquals(ProtoWriter.WIRE_TYPE_LENGTH_DELIMITED, type2)
        assertEquals("test_video_id", reader.readString())

        val (tag3, type3) = reader.readTag()!!
        assertEquals(3, tag3)
        assertEquals(ProtoWriter.WIRE_TYPE_VARINT, type3)
        assertEquals(1L, reader.readVarint())

        val (tag4, type4) = reader.readTag()!!
        assertEquals(4, tag4)
        assertEquals(ProtoWriter.WIRE_TYPE_LENGTH_DELIMITED, type4)
        val bytes = reader.readBytes()
        assertEquals(3, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
    }

    @Test
    fun testUmpReaderFrameParsing() {
        val stream = ByteArrayOutputStream()

        // Part 1: Media Header (Part 20)
        val headerPayload = ProtoWriter()
            .writeString(1, "vid_123")
            .writeInt32(2, 140)
            .writeInt64(4, 1L)
            .toByteArray()

        val part1Writer = ProtoWriter()
        part1Writer.writeVarint(20L)
        part1Writer.writeVarint(headerPayload.size.toLong())
        stream.write(part1Writer.toByteArray())
        stream.write(headerPayload)

        // Part 2: Media Data (Part 21)
        val mediaPayload = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val part2Writer = ProtoWriter()
        part2Writer.writeVarint(21L)
        part2Writer.writeVarint(mediaPayload.size.toLong())
        stream.write(part2Writer.toByteArray())
        stream.write(mediaPayload)

        // Part 3: Media End (Part 22)
        val part3Writer = ProtoWriter()
        part3Writer.writeVarint(22L)
        part3Writer.writeVarint(0L)
        stream.write(part3Writer.toByteArray())

        val parsedParts = UmpReader.parseParts(stream.toByteArray())
        assertEquals(3, parsedParts.size)
        assertEquals(20, parsedParts[0].type)
        assertEquals(21, parsedParts[1].type)
        assertEquals(22, parsedParts[2].type)

        val header = SabrProtoCodec.decodeMediaHeader(parsedParts[0].payload)
        assertEquals("vid_123", header.videoId)
        assertEquals(140, header.itag)
        assertEquals(1L, header.sequenceNumber)

        val chunks = SabrProtoCodec.demuxParts(parsedParts)
        assertEquals(2, chunks.size)
        assertEquals(4, chunks[0].data.size)
        assertFalse(chunks[0].isEnd)
        assertEquals("vid_123", chunks[0].header?.videoId)
        assertTrue(chunks[1].isEnd)
    }

    @Test
    fun testBuildAbrRequest() {
        val payload = SabrProtoCodec.buildAbrRequest(
            selectedItag = 140,
            ustreamerConfigBase64 = null,
            poToken = "test_potoken",
            sequenceNumber = 5L,
            bufferedMediaTimeMs = 12000L,
        )
        assertTrue(payload.isNotEmpty())

        val reader = ProtoReader(payload)
        val (tag1, type1) = reader.readTag()!!
        assertEquals(1, tag1) // clientAbrState
        assertEquals(ProtoWriter.WIRE_TYPE_LENGTH_DELIMITED, type1)

        val innerReader = ProtoReader(reader.readBytes())
        val (iTag1, _) = innerReader.readTag()!!
        assertEquals(1, iTag1)
        assertEquals(140L, innerReader.readVarint()) // selectedItag
    }

    @Test
    fun testSabrBootstrapFactory() {
        val json = JSONObject().apply {
            put("streamingData", JSONObject().apply {
                put("serverAbrStreamingUrl", "https://rr1---sn.googlevideo.com/videoplayback?sabr=1")
                put("adaptiveFormats", JSONArray().apply {
                    put(JSONObject().apply { put("itag", 140) })
                    put(JSONObject().apply { put("itag", 251) })
                })
            })
            put("playerConfig", JSONObject().apply {
                put("mediaCommonConfig", JSONObject().apply {
                    put("mediaUstreamerRequestConfig", JSONObject().apply {
                        put("videoPlaybackUstreamerConfig", "dGVzdF91c3RyZWFtZXI=")
                    })
                })
            })
            put("videoDetails", JSONObject().apply {
                put("lengthSeconds", "240")
            })
        }

        val bootstrap = SabrBootstrapFactory.fromPlayerResponse(json)
        assertNotNull(bootstrap)
        assertEquals("https://rr1---sn.googlevideo.com/videoplayback?sabr=1", bootstrap!!.serverAbrStreamingUrl)
        assertEquals("dGVzdF91c3RyZWFtZXI=", bootstrap.videoPlaybackUstreamerConfig)
        assertEquals(listOf(140, 251), bootstrap.candidateItags)
        assertEquals(240000L, bootstrap.durationMs)
    }

    @Test
    fun testHlsFallbackExtractionInPlayerResponseParser() {
        val json = JSONObject().apply {
            put("playabilityStatus", JSONObject().apply {
                put("status", "OK")
            })
            put("streamingData", JSONObject().apply {
                put("hlsManifestUrl", "https://manifest.googlevideo.com/api/manifest/hls_variant/index.m3u8")
                put("adaptiveFormats", JSONArray().apply {
                    put(JSONObject().apply {
                        put("itag", 140)
                        put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                        put("bitrate", 129000)
                        // Note: url is absent, exactly like iOS responses!
                    })
                })
            })
        }

        val parsed = PlayerResponseParser.parse(IosClient.IOS, json.toString())
        assertNotNull(parsed)
        assertEquals(1, parsed.candidates.size)
        val candidate = parsed.candidates.first()
        assertEquals(140, candidate.itag)
        assertEquals("https://manifest.googlevideo.com/api/manifest/hls_variant/index.m3u8", candidate.url)
        assertTrue("Stream candidate must be flagged as HLS", candidate.isHls)
        assertTrue(candidate.isAudio)
    }
}
