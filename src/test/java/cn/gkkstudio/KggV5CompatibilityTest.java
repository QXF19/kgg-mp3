package cn.gkkstudio;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Synthetic, non-musical test fixtures; no user audio, database or keys. */
public final class KggV5CompatibilityTest {
    private static final byte[] MAGIC = HexFormat.of().parseHex("7cd532eb86027f4ba8afa68e0fff9914");
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kgg-v5-compatibility-");
        SystemCapabilities.Toolchain tools = SystemCapabilities.detect();
        try {
            Path database = root.resolve("keys.db");
            run(List.of(tools.sqlite3().toString(), database.toString(),
                "CREATE TABLE ShareFileItems(EncryptionKeyId TEXT, EncryptionKey TEXT);"), null);
            Method derive = KggV5Decoder.class.getDeclaredMethod("deriveQmc2Key", String.class);
            derive.setAccessible(true);
            for (int size : new int[]{16, 256, 300, 301, 400, 512, 700}) {
                byte[] key = new byte[size];
                for (int i = 0; i < key.length; i++) key[i] = (byte)(i % 251 + 1);
                for (boolean v2 : new boolean[]{false, true}) {
                    String ekey = wrapKey(key, v2);
                    byte[] actual = (byte[]) derive.invoke(null, ekey);
                    if (!Arrays.equals(actual, key)) throw new AssertionError("Ekey mismatch " + size + "/" + v2);
                    checkChunkBoundaries(ekey);
                }
            }
            int sequence = 0;
            for (boolean rc4 : new boolean[]{false, true}) {
                for (boolean v2 : new boolean[]{false, true}) {
                    String hash = String.format("%032x", ++sequence);
                    byte[] key = new byte[rc4 ? 512 : 256];
                    for (int i = 0; i < key.length; i++) key[i] = (byte)(i % 251 + 1);
                    String ekey = wrapKey(key, v2);
                    run(List.of(tools.sqlite3().toString(), database.toString(),
                        "INSERT INTO ShareFileItems VALUES('" + hash + "','" + ekey + "');"), null);
                    String codec = v2 ? "flac" : "mp3";
                    Path audio = root.resolve("tone-" + sequence + "." + codec);
                    run(List.of(tools.ffmpeg().toString(), "-v", "error", "-f", "lavfi", "-i",
                        "sine=frequency=880:duration=2", "-c:a", v2 ? "flac" : "libmp3lame", audio.toString()), null);
                    Path encrypted = root.resolve("测试音频 " + sequence + ".kgg");
                    byte[] payload = Files.readAllBytes(audio);
                    apply(ekey, payload, 0);
                    ByteBuffer header = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
                    header.put(MAGIC); header.putInt(16, 1024); header.putInt(20, 5); header.putInt(68, 32);
                    header.position(72); header.put(hash.getBytes(StandardCharsets.US_ASCII));
                    try (OutputStream output = Files.newOutputStream(encrypted)) {
                        output.write(header.array()); output.write(payload);
                    }
                    Path output = root.resolve("output-" + sequence);
                    String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
                    run(List.of(java, "-Dfile.encoding=UTF-8", "-cp", Path.of("build/classes").toAbsolutePath().toString(),
                        "cn.gkkstudio.GkkMp3App", "--cli", encrypted.toString(), output.toString()), database);
                    Path mp3 = output.resolve("测试音频 " + sequence + ".mp3");
                    if (!Files.isRegularFile(mp3)) throw new AssertionError("Output missing");
                    run(List.of(tools.ffmpeg().toString(), "-v", "error", "-xerror", "-i", mp3.toString(), "-f", "null", "-"), null);
                    System.out.println("PASS: KGG v5 " + (rc4 ? "RC4" : "MAP") + "/" + (v2 ? "EncV2" : "V1") + "/" + codec + " -> MP3");
                }
            }
            System.out.println("PASS: 14 ekey/chunk-boundary cases and 4 end-to-end v5 conversion cases");
        } finally {
            try (var files = Files.walk(root)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                });
            }
        }
    }

    private static void checkChunkBoundaries(String ekey) throws Exception {
        byte[] whole = new byte[40000];
        new Random(42).nextBytes(whole);
        byte[] chunks = whole.clone();
        apply(ekey, whole, 0);
        int position = 0;
        while (position < chunks.length) {
            int length = Math.min(127 + position % 701, chunks.length - position);
            byte[] chunk = Arrays.copyOfRange(chunks, position, position + length);
            apply(ekey, chunk, position);
            System.arraycopy(chunk, 0, chunks, position, length);
            position += length;
        }
        if (!Arrays.equals(whole, chunks)) throw new AssertionError("Chunk boundary mismatch");
    }

    private static void apply(String ekey, byte[] payload, long offset) throws Exception {
        Method create = KggV5Decoder.class.getDeclaredMethod("createQmc2Cipher", String.class);
        create.setAccessible(true);
        Object cipher = create.invoke(null, ekey);
        Method apply = cipher.getClass().getDeclaredMethod("apply", byte[].class, int.class, long.class);
        apply.setAccessible(true); apply.invoke(cipher, payload, payload.length, offset);
    }

    private static String wrapKey(byte[] key, boolean v2) {
        byte[] teaKey = new byte[16];
        byte[] constants = HexFormat.of().parseHex("695646382b20150b");
        for (int i = 0; i < 8; i++) { teaKey[2*i] = constants[i]; teaKey[2*i+1] = key[i]; }
        byte[] body = encryptTencent(Arrays.copyOfRange(key, 8, key.length), teaKey);
        byte[] v1 = new byte[8 + body.length];
        System.arraycopy(key, 0, v1, 0, 8); System.arraycopy(body, 0, v1, 8, body.length);
        if (!v2) return Base64.getEncoder().encodeToString(v1);
        byte[] wrapped = Base64.getEncoder().encode(v1);
        wrapped = encryptTencent(wrapped, "**#!(#$%&^a1cZ,T".getBytes(StandardCharsets.US_ASCII));
        wrapped = encryptTencent(wrapped, "386ZJY!@#*$%^&)(".getBytes(StandardCharsets.US_ASCII));
        byte[] prefix = "QQMusic EncV2,Key:".getBytes(StandardCharsets.US_ASCII);
        byte[] result = Arrays.copyOf(prefix, prefix.length + wrapped.length);
        System.arraycopy(wrapped, 0, result, prefix.length, wrapped.length);
        return Base64.getEncoder().encodeToString(result);
    }

    private static byte[] encryptTencent(byte[] payload, byte[] key) {
        int pad = (8 - ((payload.length + 10) % 8)) % 8;
        byte[] plain = new byte[payload.length + 10 + pad]; plain[0] = (byte) pad;
        System.arraycopy(payload, 0, plain, 1 + pad + 2, payload.length);
        byte[] result = new byte[plain.length], previousCipher = new byte[8], previousMixed = new byte[8];
        for (int offset = 0; offset < plain.length; offset += 8) {
            byte[] mixed = new byte[8];
            for (int i = 0; i < 8; i++) mixed[i] = (byte) (plain[offset+i] ^ previousCipher[i]);
            byte[] cipher = teaEncrypt(mixed, key);
            for (int i = 0; i < 8; i++) cipher[i] ^= previousMixed[i];
            System.arraycopy(cipher, 0, result, offset, 8);
            previousCipher = cipher; previousMixed = mixed;
        }
        return result;
    }

    private static byte[] teaEncrypt(byte[] block, byte[] key) {
        ByteBuffer input = ByteBuffer.wrap(block); int a = input.getInt(), b = input.getInt();
        ByteBuffer k = ByteBuffer.wrap(key); int k0=k.getInt(), k1=k.getInt(), k2=k.getInt(), k3=k.getInt(), sum=0;
        for (int i = 0; i < 16; i++) {
            sum += 0x9E3779B9;
            a += ((b << 4) + k0) ^ (b + sum) ^ ((b >>> 5) + k1);
            b += ((a << 4) + k2) ^ (a + sum) ^ ((a >>> 5) + k3);
        }
        return ByteBuffer.allocate(8).putInt(a).putInt(b).array();
    }

    private static void run(List<String> command, Path database) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (database != null) builder.environment().put("KGG_DATABASE", database.toString());
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            process.destroyForcibly(); throw new AssertionError(new String(output, StandardCharsets.UTF_8));
        }
    }
}
