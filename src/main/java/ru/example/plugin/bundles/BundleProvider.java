package ru.example.plugin.bundles;

import mindustry.gen.Player;

import java.util.Locale;

// Данный интерфейс представляет реализацию перевода наших сообщений для игроков
// https://github.com/Kowkodivka/MindustryDevTools/blob/c32e015c63c57229422035fb94b22bd47943c2d4/bundles/src/main/java/ru/kowkodivka/tool/bundles/BundleProvider.java#L10
public interface BundleProvider {
    String get(String key, Object... args);

    String get(Player player, String key, Object... args);

    String get(Locale locale, String key, Object... args);
}
