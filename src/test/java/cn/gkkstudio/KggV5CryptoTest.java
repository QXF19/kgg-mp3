package cn.gkkstudio;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public final class KggV5CryptoTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("raw-key expected-key encrypted-db");

        Method derive = KggV5Decoder.class.getDeclaredMethod("deriveQmc2Key", String.class);
        derive.setAccessible(true);
        String encoded = Files.readString(Path.of(args[0]), StandardCharsets.US_ASCII);
        byte[] actualKey = (byte[]) derive.invoke(null, encoded);
        byte[] expectedKey = Files.readAllBytes(Path.of(args[1]));
        if (!Arrays.equals(actualKey, expectedKey)) throw new AssertionError("QMC2 ekey derivation mismatch");

        Method decryptDatabase = KggV5Decoder.class.getDeclaredMethod("decryptDatabase", byte[].class);
        decryptDatabase.setAccessible(true);
        byte[] database = (byte[]) decryptDatabase.invoke(null, (Object) Files.readAllBytes(Path.of(args[2])));
        byte[] sqliteHeader = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
        if (!Arrays.equals(Arrays.copyOf(database, sqliteHeader.length), sqliteHeader)) {
            throw new AssertionError("KuGou database decryption mismatch");
        }

        Class<?> mapClass = Class.forName("cn.gkkstudio.KggV5Decoder$Qmc2Map");
        Constructor<?> mapConstructor = mapClass.getDeclaredConstructor(byte[].class);
        mapConstructor.setAccessible(true);
        byte[] mapKey = new byte[256];
        for (int i = 0; i < mapKey.length; i++) mapKey[i] = (byte) (i * 29 + 17);
        Object map = mapConstructor.newInstance((Object) mapKey);
        Method applyMap = mapClass.getDeclaredMethod("apply", byte[].class, int.class, long.class);
        applyMap.setAccessible(true);
        byte[] mapOutput = {0};
        applyMap.invoke(map, mapOutput, 1, 0L);
        if ((mapOutput[0] & 0xFF) != 0x1D) throw new AssertionError("QMC2 MAP known vector mismatch");

        Class<?> rc4Class = Class.forName("cn.gkkstudio.KggV5Decoder$Qmc2Rc4");
        Constructor<?> rc4Constructor = rc4Class.getDeclaredConstructor(byte[].class);
        rc4Constructor.setAccessible(true);
        byte[] rc4Key = new byte[400];
        for (int i = 0; i < rc4Key.length; i++) rc4Key[i] = (byte) (i * 7 + 3);
        Object rc4 = rc4Constructor.newInstance((Object) rc4Key);
        Method applyRc4 = rc4Class.getDeclaredMethod("apply", byte[].class, int.class, long.class);
        applyRc4.setAccessible(true);
        byte[] rc4Output = new byte[16];
        applyRc4.invoke(rc4, rc4Output, rc4Output.length, 0x1400L);
        byte[] rc4Expected = {
            (byte) 0xC5, (byte) 0xDA, 0x55, 0x61, (byte) 0xB1, (byte) 0xA5, 0x7E, (byte) 0xEA,
            0x47, 0x05, 0x51, (byte) 0x8A, (byte) 0x81, 0x61, (byte) 0xDE, 0x6A
        };
        if (!Arrays.equals(rc4Output, rc4Expected)) throw new AssertionError("QMC2 RC4 known vector mismatch");

        System.out.println("PASS: KGG v5 ekey, database, MAP and RC4 vectors");
    }
}
