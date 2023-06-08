package ru.example.plugin.bundles;

import mindustry.gen.Player;

import java.util.Locale;

// Данный интерфейс представляет реализацию перевода наших сообщений для игроков
public interface BundleProvider {
    String get(String key, Object... args);

    String get(Player player, String key, Object... args);

    String get(Locale locale, String key, Object... args);
}
