package cn.gkkstudio;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Decodes KuGou KGG/KGM crypto version 5 containers.
 *
 * Version 5 uses a per-track QMC2 key stored in KuGou's local KGMusicV3.db.
 * The database is decrypted only into a temporary file and deleted immediately
 * after SQLite has read the key mapping.
 */
final class KggV5Decoder {
    private static final byte[] KGM_MAGIC = {
        0x7C, (byte) 0xD5, 0x32, (byte) 0xEB, (byte) 0x86, 0x02, 0x7F, 0x4B,
        (byte) 0xA8, (byte) 0xAF, (byte) 0xA6, (byte) 0x8E, 0x0F, (byte) 0xFF, (byte) 0x99, 0x14
    };
    private static final byte[] VPR_MAGIC = {
        0x05, 0x28, (byte) 0xBC, (byte) 0x96, (byte) 0xE9, (byte) 0xE4, 0x5A, 0x43,
        (byte) 0x91, (byte) 0xAA, (byte) 0xBD, (byte) 0xD0, 0x7A, (byte) 0xF5, 0x36, 0x31
    };
    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DEFAULT_DB_MASTER_KEY = {
        0x1D, 0x61, 0x31, 0x45, (byte) 0xB2, 0x47, (byte) 0xBF, 0x7F,
        0x3D, 0x18, (byte) 0x96, 0x72, 0x14, 0x4F, (byte) 0xE4, (byte) 0xBF
    };
    private static final byte[] V2_PREFIX = "QQMusic EncV2,Key:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] V2_TEA_KEY_1 = "386ZJY!@#*$%^&)(".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] V2_TEA_KEY_2 = "**#!(#$%&^a1cZ,T".getBytes(StandardCharsets.US_ASCII);
    private static final int DB_PAGE_SIZE = 0x400;
    private static final Object KEY_CACHE_LOCK = new Object();
    private static String cachedDatabaseSignature;
    private static Map<String, String> cachedKeys = Map.of();

    private KggV5Decoder() {}

    static boolean supports(Path source) throws IOException {
        if (Files.size(source) < 0x48) return false;
        byte[] header = new byte[0x18];
        try (InputStream input = Files.newInputStream(source)) {
            if (input.readNBytes(header, 0, header.length) != header.length) return false;
        }
        return (startsWith(header, KGM_MAGIC) || startsWith(header, VPR_MAGIC))
            && littleEndianInt(header, 0x14) == 5;
    }

    static Path decodeToTemporaryFile(Path source, SystemCapabilities.Toolchain tools) throws IOException {
        Header header = readHeader(source);
        if (tools.sqlite3() == null) {
            throw new IOException("新版 KGG 解密组件 sqlite3.exe 缺失，请重新下载完整便携版");
        }
        String encryptedKey = lookupKeyFile(header.audioHash());
        if (encryptedKey == null) {
            Path database = locateKuGouDatabase();
            encryptedKey = loadKeyMap(database, tools.sqlite3()).get(header.audioHash());
        }
        if (encryptedKey == null || encryptedKey.isBlank()) {
            throw new IOException("已支持此 KGG v5 文件，但本机数据库没有它的专属密钥。请先用酷狗客户端播放这一个文件，再重新转换");
        }

        StreamCipher cipher = createQmc2Cipher(encryptedKey);
        Path output = Files.createTempFile("kgg-v5-decoded-", ".audio");
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source));
             OutputStream decoded = new BufferedOutputStream(Files.newOutputStream(output))) {
            input.skipNBytes(header.audioOffset());
            byte[] buffer = new byte[64 * 1024];
            long offset = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                cipher.apply(buffer, read, offset);
                decoded.write(buffer, 0, read);
                offset += read;
            }
            return output;
        } catch (Exception error) {
            Files.deleteIfExists(output);
            if (error instanceof IOException io) throw io;
            throw new IOException("新版 KGG 解密失败: " + error.getMessage(), error);
        }
    }

    private static Header readHeader(Path source) throws IOException {
        byte[] fixed = new byte[0x48];
        try (InputStream input = Files.newInputStream(source)) {
            if (input.readNBytes(fixed, 0, fixed.length) != fixed.length) {
                throw new IOException("KGG 文件头不完整");
            }
            if (!startsWith(fixed, KGM_MAGIC) && !startsWith(fixed, VPR_MAGIC)) {
                throw new IOException("不是有效的 KGG/KGM 文件");
            }
            int version = littleEndianInt(fixed, 0x14);
            if (version != 5) throw new IOException("不支持的 KGG 加密版本: " + version);
            long audioOffset = Integer.toUnsignedLong(littleEndianInt(fixed, 0x10));
            int hashLength = littleEndianInt(fixed, 0x44);
            if (audioOffset < 0x48 || audioOffset >= Files.size(source) || hashLength <= 0 || hashLength > 256
                || audioOffset < 0x48L + hashLength) {
                throw new IOException("KGG 文件头参数无效");
            }
            byte[] hash = input.readNBytes(hashLength);
            if (hash.length != hashLength) throw new IOException("KGG 歌曲标识不完整");
            return new Header(audioOffset, new String(hash, StandardCharsets.US_ASCII));
        }
    }

    private static Path locateKuGouDatabase() throws IOException {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getenv("KGG_DATABASE");
        if (configured != null && !configured.isBlank()) candidates.add(Path.of(configured));
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            candidates.add(Path.of(appData, "KuGou8", "KGMusicV3.db"));
        }
        candidates.add(Path.of("").toAbsolutePath().resolve("tools").resolve("KGMusicV3.db"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        throw new IOException("未找到酷狗密钥数据库 KGMusicV3.db。请安装并登录酷狗客户端，播放一次歌曲后重试");
    }

    private static String lookupKeyFile(String audioHash) throws IOException {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getenv("KGG_KEY_FILE");
        if (configured != null && !configured.isBlank()) candidates.add(Path.of(configured));
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd.resolve("tools").resolve("kgg.key"));
        try {
            Path app = Path.of(KggV5Decoder.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize().getParent();
            candidates.add(app.resolve("tools").resolve("kgg.key"));
        } catch (Exception ignored) {
        }
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                int separator = line.indexOf('$');
                if (separator > 0 && line.substring(0, separator).equals(audioHash)) {
                    return line.substring(separator + 1).strip();
                }
            }
        }
        return null;
    }

    private static Map<String, String> loadKeyMap(Path database, Path sqlite3) throws IOException {
        String signature = database + ":" + Files.size(database) + ":" + Files.getLastModifiedTime(database).toMillis();
        synchronized (KEY_CACHE_LOCK) {
            if (signature.equals(cachedDatabaseSignature)) return cachedKeys;
            byte[] encrypted = Files.readAllBytes(database);
            byte[] plain = decryptDatabase(encrypted);
            Path temporaryDatabase = Files.createTempFile("kgg-key-database-", ".db");
            try {
                Files.write(temporaryDatabase, plain);
                cachedKeys = Collections.unmodifiableMap(queryKeys(sqlite3, temporaryDatabase));
                cachedDatabaseSignature = signature;
                return cachedKeys;
            } finally {
                Arrays.fill(plain, (byte) 0);
                Files.deleteIfExists(temporaryDatabase);
            }
        }
    }

    private static byte[] decryptDatabase(byte[] encrypted) throws IOException {
        if (encrypted.length >= SQLITE_HEADER.length &&
            Arrays.equals(Arrays.copyOf(encrypted, SQLITE_HEADER.length), SQLITE_HEADER)) {
            return encrypted;
        }
        if (encrypted.length == 0 || encrypted.length % DB_PAGE_SIZE != 0) {
            throw new IOException("酷狗数据库长度无效");
        }
        byte[] masterKey = configuredMasterKey();
        byte[] result = encrypted.clone();
        byte[] expected = Arrays.copyOfRange(result, 0x10, 0x18);
        System.arraycopy(result, 0x08, result, 0x10, 8);
        decryptAesPage(result, 0x10, DB_PAGE_SIZE - 0x10, 1, masterKey);
        if (!Arrays.equals(expected, Arrays.copyOfRange(result, 0x10, 0x18))) {
            throw new IOException("酷狗数据库解密校验失败，可能是不受支持的客户端版本");
        }
        System.arraycopy(SQLITE_HEADER, 0, result, 0, SQLITE_HEADER.length);
        for (int page = 2, offset = DB_PAGE_SIZE; offset < result.length; page++, offset += DB_PAGE_SIZE) {
            decryptAesPage(result, offset, DB_PAGE_SIZE, page, masterKey);
        }
        return result;
    }

    private static byte[] configuredMasterKey() throws IOException {
        String configured = System.getenv("KGG_DB_MASTER_KEY");
        if (configured == null || configured.isBlank()) return DEFAULT_DB_MASTER_KEY.clone();
        String hex = configured.strip();
        if (hex.length() != 32) throw new IOException("KGG_DB_MASTER_KEY 必须是 32 位十六进制字符串");
        byte[] key = new byte[16];
        try {
            for (int i = 0; i < key.length; i++) key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            return key;
        } catch (NumberFormatException error) {
            throw new IOException("KGG_DB_MASTER_KEY 包含无效字符", error);
        }
    }

    private static void decryptAesPage(byte[] data, int offset, int length, int page, byte[] masterKey) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            ByteBuffer keySeed = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            keySeed.put(masterKey).putInt(page).putInt(0x546C4173);
            byte[] key = md5.digest(keySeed.array());

            ByteBuffer ivSeed = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            int seed = page + 1;
            for (int i = 0; i < 4; i++) {
                seed = nextPageIv(seed);
                ivSeed.putInt(seed);
            }
            byte[] iv = md5.digest(ivSeed.array());
            Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] plain = aes.doFinal(data, offset, length);
            System.arraycopy(plain, 0, data, offset, length);
        } catch (Exception error) {
            throw new IOException("酷狗数据库第 " + page + " 页解密失败", error);
        }
    }

    private static int nextPageIv(int seed) {
        int left = seed * 0x9EF4;
        int right = Integer.divideUnsigned(seed, 0xCE26) * 0x7FFFFF07;
        int value = left - right;
        return (value & 0x80000000) == 0 ? value : value + 0x7FFFFF07;
    }

    private static Map<String, String> queryKeys(Path sqlite3, Path database) throws IOException {
        String sql = "SELECT EncryptionKeyId, EncryptionKey FROM ShareFileItems " +
            "WHERE EncryptionKeyId IS NOT NULL AND EncryptionKeyId != '' " +
            "AND EncryptionKey IS NOT NULL AND EncryptionKey != '';";
        Process process = new ProcessBuilder(sqlite3.toString(), "-readonly", "-batch", "-noheader",
            "-separator", "\t", database.toString(), sql).redirectErrorStream(true).start();
        byte[] output;
        try {
            output = process.getInputStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("读取酷狗密钥数据库超时");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("读取酷狗密钥数据库被中断", error);
        }
        String text = new String(output, StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException("读取酷狗密钥数据库失败: " + text.strip());
        Map<String, String> mapping = new HashMap<>();
        for (String line : text.split("\\R")) {
            int tab = line.indexOf('\t');
            if (tab > 0 && tab + 1 < line.length()) mapping.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return mapping;
    }

    private static StreamCipher createQmc2Cipher(String encryptedKey) throws IOException {
        byte[] key = deriveQmc2Key(encryptedKey);
        return key.length > 300 ? new Qmc2Rc4(key) : new Qmc2Map(key);
    }

    private static byte[] deriveQmc2Key(String encryptedKey) throws IOException {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptedKey);
        } catch (IllegalArgumentException error) {
            throw new IOException("歌曲密钥不是有效的 Base64 数据", error);
        }
        if (startsWith(decoded, V2_PREFIX)) {
            decoded = Arrays.copyOfRange(decoded, V2_PREFIX.length, decoded.length);
            decoded = tencentTeaDecrypt(decoded, V2_TEA_KEY_1);
            decoded = tencentTeaDecrypt(decoded, V2_TEA_KEY_2);
            try {
                decoded = Base64.getDecoder().decode(decoded);
            } catch (IllegalArgumentException error) {
                throw new IOException("歌曲 V2 密钥解码失败", error);
            }
        }
        if (decoded.length < 16) throw new IOException("歌曲密钥长度过短");
        byte[] teaKey = new byte[16];
        for (int i = 0; i < 8; i++) {
            teaKey[i * 2] = (byte) (Math.abs(Math.tan(106d + i * 0.1d)) * 100d);
            teaKey[i * 2 + 1] = decoded[i];
        }
        byte[] body = tencentTeaDecrypt(Arrays.copyOfRange(decoded, 8, decoded.length), teaKey);
        byte[] result = new byte[8 + body.length];
        System.arraycopy(decoded, 0, result, 0, 8);
        System.arraycopy(body, 0, result, 8, body.length);
        return result;
    }

    private static byte[] tencentTeaDecrypt(byte[] encrypted, byte[] key) throws IOException {
        if (encrypted.length < 16 || encrypted.length % 8 != 0) {
            throw new IOException("Tencent TEA 密文长度无效");
        }
        byte[] block = teaDecryptBlock(Arrays.copyOfRange(encrypted, 0, 8), key);
        int padding = block[0] & 7;
        int outputLength = encrypted.length - 1 - padding - 2 - 7;
        if (outputLength < 0) throw new IOException("Tencent TEA 填充无效");

        byte[] output = new byte[outputLength];
        byte[] previousCipher = new byte[8];
        byte[] currentCipher = Arrays.copyOfRange(encrypted, 0, 8);
        int inputPosition = 8;
        int blockPosition = 1 + padding;

        for (int salt = 0; salt < 2; salt++) {
            if (blockPosition == 8) {
                byte[][] state = nextTencentBlock(encrypted, inputPosition, block, currentCipher, key);
                previousCipher = state[0];
                currentCipher = state[1];
                block = state[2];
                inputPosition += 8;
                blockPosition = 0;
            }
            blockPosition++;
        }

        for (int outputPosition = 0; outputPosition < output.length; outputPosition++) {
            if (blockPosition == 8) {
                byte[][] state = nextTencentBlock(encrypted, inputPosition, block, currentCipher, key);
                previousCipher = state[0];
                currentCipher = state[1];
                block = state[2];
                inputPosition += 8;
                blockPosition = 0;
            }
            output[outputPosition] = (byte) (block[blockPosition] ^ previousCipher[blockPosition]);
            blockPosition++;
        }
        for (int zero = 0; zero < 7; zero++) {
            if (blockPosition == 8) {
                byte[][] state = nextTencentBlock(encrypted, inputPosition, block, currentCipher, key);
                previousCipher = state[0];
                currentCipher = state[1];
                block = state[2];
                inputPosition += 8;
                blockPosition = 0;
            }
            if (block[blockPosition] != previousCipher[blockPosition]) {
                throw new IOException("Tencent TEA 完整性校验失败");
            }
            blockPosition++;
        }
        return output;
    }

    private static byte[][] nextTencentBlock(byte[] encrypted, int inputPosition, byte[] block,
                                              byte[] currentCipher, byte[] key) throws IOException {
        if (inputPosition + 8 > encrypted.length) throw new IOException("Tencent TEA 数据被截断");
        byte[] previous = currentCipher;
        byte[] current = Arrays.copyOfRange(encrypted, inputPosition, inputPosition + 8);
        byte[] mixed = new byte[8];
        for (int i = 0; i < 8; i++) mixed[i] = (byte) (block[i] ^ current[i]);
        return new byte[][] {previous, current, teaDecryptBlock(mixed, key)};
    }

    private static byte[] teaDecryptBlock(byte[] input, byte[] key) {
        ByteBuffer in = ByteBuffer.wrap(input).order(ByteOrder.BIG_ENDIAN);
        int left = in.getInt();
        int right = in.getInt();
        ByteBuffer keys = ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN);
        int k0 = keys.getInt(), k1 = keys.getInt(), k2 = keys.getInt(), k3 = keys.getInt();
        int sum = 0xE3779B90;
        for (int round = 0; round < 16; round++) {
            right -= ((left << 4) + k2) ^ (left + sum) ^ ((left >>> 5) + k3);
            left -= ((right << 4) + k0) ^ (right + sum) ^ ((right >>> 5) + k1);
            sum -= 0x9E3779B9;
        }
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(left).putInt(right).array();
    }

    private interface StreamCipher {
        void apply(byte[] data, int length, long offset);
    }

    private static final class Qmc2Map implements StreamCipher {
        private final byte[] key;

        Qmc2Map(byte[] key) {
            this.key = key.clone();
        }

        @Override public void apply(byte[] data, int length, long offset) {
            for (int i = 0; i < length; i++) {
                long position = offset + i;
                if (position > 0x7FFF) position %= 0x7FFF;
                int index = (int) ((position * position + 71214) % key.length);
                int value = key[index] & 0xFF;
                int shift = ((index & 7) + 4) % 8;
                int mask = ((value << shift) | (value >>> shift)) & 0xFF;
                data[i] ^= (byte) mask;
            }
        }
    }

    private static final class Qmc2Rc4 implements StreamCipher {
        private static final int SEGMENT_SIZE = 5120;
        private static final int FIRST_SEGMENT_SIZE = 128;
        private final int[] initialBox;
        private final byte[] key;
        private final long hash;

        Qmc2Rc4(byte[] key) {
            this.key = key.clone();
            this.initialBox = new int[key.length];
            for (int i = 0; i < initialBox.length; i++) initialBox[i] = i & 0xFF;
            int j = 0;
            for (int i = 0; i < initialBox.length; i++) {
                j = (j + initialBox[i] + (key[i] & 0xFF)) % initialBox.length;
                int swap = initialBox[i];
                initialBox[i] = initialBox[j];
                initialBox[j] = swap;
            }
            long value = 1;
            for (byte element : key) {
                int factor = element & 0xFF;
                if (factor == 0) continue;
                long next = (value * factor) & 0xFFFFFFFFL;
                if (next == 0 || next <= value) break;
                value = next;
            }
            this.hash = value;
        }

        @Override public void apply(byte[] data, int length, long streamOffset) {
            int processed = 0;
            long offset = streamOffset;
            if (offset < FIRST_SEGMENT_SIZE) {
                int block = (int) Math.min(length, FIRST_SEGMENT_SIZE - offset);
                for (int i = 0; i < block; i++) data[i] ^= key[segmentSkip((int) offset + i)];
                processed += block;
                offset += block;
            }
            while (processed < length) {
                int block = Math.min(length - processed, SEGMENT_SIZE - (int) (offset % SEGMENT_SIZE));
                applySegment(data, processed, block, offset);
                processed += block;
                offset += block;
            }
        }

        private void applySegment(byte[] data, int start, int length, long offset) {
            int[] box = initialBox.clone();
            int j = 0;
            int k = 0;
            int skip = (int) (offset % SEGMENT_SIZE) + segmentSkip((int) (offset / SEGMENT_SIZE));
            for (int i = -skip; i < length; i++) {
                j = (j + 1) % box.length;
                k = (box[j] + k) % box.length;
                int swap = box[j];
                box[j] = box[k];
                box[k] = swap;
                if (i >= 0) data[start + i] ^= (byte) box[(box[j] + box[k]) % box.length];
            }
        }

        private int segmentSkip(int id) {
            int seed = key[Math.floorMod(id, key.length)] & 0xFF;
            if (seed == 0) return 0;
            long index = (long) (hash / ((double) (id + 1) * seed) * 100d);
            return (int) Math.floorMod(index, key.length);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }

    private record Header(long audioOffset, String audioHash) {}
}
