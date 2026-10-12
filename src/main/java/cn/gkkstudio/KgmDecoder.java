package cn.gkkstudio;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Decodes legacy KuGou KGM/KGMA/VPR containers into their original audio stream.
 *
 * Algorithm and mask data are based on Unlock Music (MIT License).
 * See THIRD_PARTY_NOTICES.md.
 */
final class KgmDecoder {
    private static final int MIN_HEADER_LENGTH = 0x400;
    private static final int OWN_KEY_LENGTH = 17;
    private static final byte[] KGM_HEADER = {
        0x7C, (byte) 0xD5, 0x32, (byte) 0xEB, (byte) 0x86, 0x02, 0x7F, 0x4B,
        (byte) 0xA8, (byte) 0xAF, (byte) 0xA6, (byte) 0x8E, 0x0F, (byte) 0xFF, (byte) 0x99, 0x14
    };
    private static final byte[] VPR_HEADER = {
        0x05, 0x28, (byte) 0xBC, (byte) 0x96, (byte) 0xE9, (byte) 0xE4, 0x5A, 0x43,
        (byte) 0x91, (byte) 0xAA, (byte) 0xBD, (byte) 0xD0, 0x7A, (byte) 0xF5, 0x36, 0x31
    };
    private static final byte[] VPR_MASK_DIFF = {
        0x25, (byte) 0xDF, (byte) 0xE8, (byte) 0xA6, 0x75, 0x1E, 0x75, 0x0E,
        0x2F, (byte) 0x80, (byte) 0xF3, 0x2D, (byte) 0xB8, (byte) 0xB6, (byte) 0xE3, 0x11, 0x00
    };
    private static final byte[] MASK_MEND = {
        (byte)0xB8,(byte)0xD5,0x3D,(byte)0xB2,(byte)0xE9,(byte)0xAF,0x78,(byte)0x8C,(byte)0x83,0x33,0x71,0x51,0x76,(byte)0xA0,(byte)0xCD,0x37,
        0x2F,0x3E,0x35,(byte)0x8D,(byte)0xA9,(byte)0xBE,(byte)0x98,(byte)0xB7,(byte)0xE7,(byte)0x8C,0x22,(byte)0xCE,0x5A,0x61,(byte)0xDF,0x68,
        0x69,(byte)0x89,(byte)0xFE,(byte)0xA5,(byte)0xB6,(byte)0xDE,(byte)0xA9,0x77,(byte)0xFC,(byte)0xC8,(byte)0xBD,(byte)0xBD,(byte)0xE5,0x6D,0x3E,0x5A,
        0x36,(byte)0xEF,0x69,0x4E,(byte)0xBE,(byte)0xE1,(byte)0xE9,0x66,0x1C,(byte)0xF3,(byte)0xD9,0x02,(byte)0xB6,(byte)0xF2,0x12,(byte)0x9B,
        0x44,(byte)0xD0,0x6F,(byte)0xB9,0x35,(byte)0x89,(byte)0xB6,0x46,0x6D,0x73,(byte)0x82,0x06,0x69,(byte)0xC1,(byte)0xED,(byte)0xD7,
        (byte)0x85,(byte)0xC2,0x30,(byte)0xDF,(byte)0xA2,0x62,(byte)0xBE,0x79,0x2D,0x62,0x62,0x3D,0x0D,0x7E,(byte)0xBE,0x48,
        (byte)0x89,0x23,0x02,(byte)0xA0,(byte)0xE4,(byte)0xD5,0x75,0x51,0x32,0x02,0x53,(byte)0xFD,0x16,0x3A,0x21,0x3B,
        0x16,0x0F,(byte)0xC3,(byte)0xB2,(byte)0xBB,(byte)0xB3,(byte)0xE2,(byte)0xBA,0x3A,0x3D,0x13,(byte)0xEC,(byte)0xF6,0x01,0x45,(byte)0x84,
        (byte)0xA5,0x70,0x0F,(byte)0x93,0x49,0x0C,0x64,(byte)0xCD,0x31,(byte)0xD5,(byte)0xCC,0x4C,0x07,0x01,(byte)0x9E,0x00,
        0x1A,0x23,(byte)0x90,(byte)0xBF,(byte)0x88,0x1E,0x3B,(byte)0xAB,(byte)0xA6,0x3E,(byte)0xC4,0x73,0x47,0x10,0x7E,0x3B,
        0x5E,(byte)0xBC,(byte)0xE3,0x00,(byte)0x84,(byte)0xFF,0x09,(byte)0xD4,(byte)0xE0,(byte)0x89,0x0F,0x5B,0x58,0x70,0x4F,(byte)0xFB,
        0x65,(byte)0xD8,0x5C,0x53,0x1B,(byte)0xD3,(byte)0xC8,(byte)0xC6,(byte)0xBF,(byte)0xEF,(byte)0x98,(byte)0xB0,0x50,0x4F,0x0F,(byte)0xEA,
        (byte)0xE5,(byte)0x83,0x58,(byte)0x8C,0x28,0x2C,(byte)0x84,0x67,(byte)0xCD,(byte)0xD0,(byte)0x9E,0x47,(byte)0xDB,0x27,0x50,(byte)0xCA,
        (byte)0xF4,0x63,0x63,(byte)0xE8,(byte)0x97,0x7F,0x1B,0x4B,0x0C,(byte)0xC2,(byte)0xC1,0x21,0x4C,(byte)0xCC,0x58,(byte)0xF5,
        (byte)0x94,0x52,(byte)0xA3,(byte)0xF3,(byte)0xD3,(byte)0xE0,0x68,(byte)0xF4,0x00,0x23,(byte)0xF3,0x5E,0x0A,0x7B,(byte)0x93,(byte)0xDD,
        (byte)0xAB,0x12,(byte)0xB2,0x13,(byte)0xE8,(byte)0x84,(byte)0xD7,(byte)0xA7,(byte)0x9F,0x0F,0x32,0x4C,0x55,0x1D,0x04,0x36,
        0x52,(byte)0xDC,0x03,(byte)0xF3,(byte)0xF9,0x4E,0x42,(byte)0xE9,0x3D,0x61,(byte)0xEF,0x7C,(byte)0xB6,(byte)0xB3,(byte)0x93,0x50
    };

    private KgmDecoder() {}

    static boolean supports(Path source) throws IOException {
        byte[] header = new byte[0x18];
        try (InputStream input = Files.newInputStream(source)) {
            if (input.readNBytes(header, 0, header.length) != header.length) return false;
        }
        boolean matched = startsWith(header, KGM_HEADER) || startsWith(header, VPR_HEADER);
        return matched && littleEndianInt(header, 0x14) != 5;
    }

    static Path decodeToTemporaryFile(Path source) throws IOException {
        byte[] header = new byte[MIN_HEADER_LENGTH];
        Path output = null;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
            if (input.readNBytes(header, 0, header.length) != header.length) {
                throw new IOException("KGM 文件头不完整");
            }
            boolean vpr = startsWith(header, VPR_HEADER);
            if (!vpr && !startsWith(header, KGM_HEADER)) throw new IOException("不是有效的 KGM/VPR 文件");

            int headerLength = littleEndianInt(header, 0x10);
            if (headerLength < MIN_HEADER_LENGTH || headerLength >= Files.size(source)) {
                throw new IOException("KGM 文件头长度无效");
            }
            if (headerLength > MIN_HEADER_LENGTH) input.skipNBytes(headerLength - MIN_HEADER_LENGTH);

            long encryptedLength = Files.size(source) - headerLength;
            if (((encryptedLength - 1) >>> 4) >= MaskHolder.MASK.length) {
                throw new IOException("KGM 音频数据超过当前支持的 64 MB 上限");
            }
            byte[] ownKey = new byte[OWN_KEY_LENGTH];
            System.arraycopy(header, 0x1C, ownKey, 0, 16);

            output = Files.createTempFile("kgg-decoded-", ".audio");
            try (OutputStream decoded = new BufferedOutputStream(Files.newOutputStream(output))) {
                byte[] buffer = new byte[64 * 1024];
                long position = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    for (int offset = 0; offset < read; offset++, position++) {
                        int value = decodeByte(buffer[offset] & 0xFF, position, ownKey);
                        if (vpr) value ^= VPR_MASK_DIFF[(int) (position % VPR_MASK_DIFF.length)] & 0xFF;
                        buffer[offset] = (byte) value;
                    }
                    decoded.write(buffer, 0, read);
                }
            }
            return output;
        } catch (Exception error) {
            if (output != null) Files.deleteIfExists(output);
            if (error instanceof IOException io) throw io;
            throw new IOException("KGM 解码失败: " + error.getMessage(), error);
        }
    }

    static void createEncryptedFixture(Path plainAudio, Path encryptedTarget) throws IOException {
        byte[] header = new byte[MIN_HEADER_LENGTH];
        System.arraycopy(KGM_HEADER, 0, header, 0, KGM_HEADER.length);
        writeLittleEndianInt(header, 0x10, MIN_HEADER_LENGTH);
        byte[] ownKey = new byte[OWN_KEY_LENGTH];
        for (int i = 0; i < 16; i++) ownKey[i] = (byte) (0x31 + i * 7);
        System.arraycopy(ownKey, 0, header, 0x1C, 16);

        try (InputStream input = new BufferedInputStream(Files.newInputStream(plainAudio));
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(encryptedTarget))) {
            output.write(header);
            byte[] buffer = new byte[64 * 1024];
            long position = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                for (int offset = 0; offset < read; offset++, position++) {
                    buffer[offset] = (byte) encodeByte(buffer[offset] & 0xFF, position, ownKey);
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static int decodeByte(int encrypted, long position, byte[] ownKey) {
        int keyMixed = (ownKey[(int) (position % ownKey.length)] & 0xFF) ^ encrypted;
        return foldNibble(keyMixed) ^ foldNibble(maskByte(position));
    }

    private static int encodeByte(int plain, long position, byte[] ownKey) {
        int foldedMask = foldNibble(maskByte(position));
        int key = ownKey[(int) (position % ownKey.length)] & 0xFF;
        return key ^ foldNibble(plain ^ foldedMask);
    }

    private static int maskByte(long position) {
        return (MASK_MEND[(int) (position % MASK_MEND.length)] & 0xFF)
            ^ (MaskHolder.MASK[(int) (position >>> 4)] & 0xFF);
    }

    private static int foldNibble(int value) {
        value &= 0xFF;
        return (value ^ ((value & 0x0F) << 4)) & 0xFF;
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeLittleEndianInt(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static final class MaskHolder {
        static final byte[] MASK = loadMask();

        private static byte[] loadMask() {
            try (InputStream resource = KgmDecoder.class.getResourceAsStream("/cn/gkkstudio/kgm-mask-v2.gz")) {
                if (resource == null) throw new IOException("内置 KGM 密钥资源缺失");
                try (GZIPInputStream gzip = new GZIPInputStream(resource)) {
                    byte[] mask = gzip.readAllBytes();
                    if (mask.length != 4 * 1024 * 1024) throw new IOException("内置 KGM 密钥资源长度无效");
                    return mask;
                }
            } catch (IOException error) {
                throw new ExceptionInInitializerError(error);
            }
        }
    }
}
