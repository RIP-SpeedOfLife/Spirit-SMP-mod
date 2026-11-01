package com.soulsmp;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SoulState {
    private SoulState() {}
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> DURATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, String>> MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, Integer>> DAMAGE = new ConcurrentHashMap<>();

    // ---------- generic persistent values stored as entity tags ----------
    public static String tag(ServerPlayer p, String prefix) {
        for (String t : Compat.tags(p)) if (t.startsWith(prefix)) return t.substring(prefix.length());
        return "";
    }
    public static void tag(ServerPlayer p, String prefix, String value) {
        Compat.tags(p).stream().filter(t -> t.startsWith(prefix)).toList().forEach(p::removeTag);
        if (value != null && !value.isEmpty()) p.addTag(prefix + value);
    }
    public static long tagLong(ServerPlayer p, String prefix) {
        try { return Long.parseLong(tag(p, prefix)); } catch (Exception e) { return 0L; }
    }

    // ---------- spirit ----------
    public static String spirit(ServerPlayer p) {
        for (String tag : Compat.tags(p)) if (tag.startsWith("soulsmp:spirit=")) return tag.substring("soulsmp:spirit=".length());
        return "";
    }
    public static void spirit(ServerPlayer p, String s) {
        Compat.tags(p).stream().filter(t -> t.startsWith("soulsmp:spirit=")).toList().forEach(p::removeTag);
        if (s != null && !s.isBlank()) p.addTag("soulsmp:spirit=" + s);
    }
    public static boolean has(ServerPlayer p, String s) { return s.equals(spirit(p)); }

    /** The spirit actually in effect: Thief's borrowed spirit (while it lasts) or Random's copied spirit. */
    public static String effective(ServerPlayer p) {
        String s = spirit(p);
        if (s.equals("Thief Spirit")) {
            String b = tag(p, "soulsmp:borrow=");
            if (!b.isEmpty() && System.currentTimeMillis() < tagLong(p, "soulsmp:borrowuntil=")) return b;
        } else if (s.equals("Random Spirit")) {
            String c = tag(p, "soulsmp:copy=");
            if (!c.isEmpty()) return c;
        }
        return s;
    }
    public static boolean is(ServerPlayer p, String s) { return s.equals(effective(p)); }

    public static int lifestealHearts(ServerPlayer p) {
        for(String tag:Compat.tags(p)) if(tag.startsWith("soulsmp:lifesteal=")) try{return Integer.parseInt(tag.substring("soulsmp:lifesteal=".length()));}catch(Exception ignored){}
        return 0;
    }
    public static void lifestealHearts(ServerPlayer p,int n){Compat.tags(p).stream().filter(t->t.startsWith("soulsmp:lifesteal=")).toList().forEach(p::removeTag);p.addTag("soulsmp:lifesteal="+n);}

    // ---------- cooldowns (long ones survive restarts through tags) ----------
    public static long getCd(ServerPlayer p, String key) {
        Long m = COOLDOWNS.getOrDefault(p.getUUID(), Map.of()).get(key);
        if (m != null) return m;
        long t = tagLong(p, "soulsmp:cd:" + key + "=");
        if (t > 0) COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(key, t);
        return t;
    }
    public static void setCd(ServerPlayer p, String key, long when) {
        COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(key, when);
        if (when - System.currentTimeMillis() >= 60_000L) tag(p, "soulsmp:cd:" + key + "=", Long.toString(when));
        else tag(p, "soulsmp:cd:" + key + "=", "");
    }
    public static boolean ready(ServerPlayer p, String key) { return System.currentTimeMillis() >= getCd(p,key); }
    public static void startCd(ServerPlayer p, String key, long duration) { setCd(p,key,System.currentTimeMillis()+duration); }
    public static void resetAll(ServerPlayer p) {
        COOLDOWNS.remove(p.getUUID()); DURATIONS.remove(p.getUUID()); MODES.remove(p.getUUID());
        Compat.tags(p).stream().filter(t -> t.startsWith("soulsmp:cd:")).toList().forEach(p::removeTag);
        tag(p, "soulsmp:xpm=", ""); tag(p, "soulsmp:destiny=", ""); tag(p, "soulsmp:destinyuntil=", ""); tag(p, "soulsmp:copy=", ""); tag(p, "soulsmp:borrow=", ""); tag(p, "soulsmp:borrowuntil=", "");
    }

    public static long duration(ServerPlayer p, String key) { return DURATIONS.getOrDefault(p.getUUID(), Map.of()).getOrDefault(key, 0L); }
    public static void setDuration(ServerPlayer p, String key, long until) { DURATIONS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(key, until); }
    public static String getMode(ServerPlayer p,String key){return MODES.getOrDefault(p.getUUID(),Map.of()).getOrDefault(key,"");}
    public static void setMode(ServerPlayer p,String key,String value){MODES.computeIfAbsent(p.getUUID(),k->new ConcurrentHashMap<>()).put(key,value);}
    public static void rememberDamage(ServerPlayer p,int slot,int damage){DAMAGE.computeIfAbsent(p.getUUID(),k->new ConcurrentHashMap<>()).put(slot,damage);}
    public static Integer rememberedDamage(ServerPlayer p,int slot){return DAMAGE.getOrDefault(p.getUUID(),Map.of()).get(slot);}
}
