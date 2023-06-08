package ru.example.plugin.bundles;

import arc.util.Strings;
import mindustry.gen.Player;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class DefaultBundler implements BundleProvider {
    // Стандартная локаль
    private final Locale defaultLocale = new Locale("en");
    // Файл, который будет содержать локализованные строки для стандартной локали
    private final ResourceBundle defaultBundle = ResourceBundle.getBundle("bundles/bundle", defaultLocale);

    @Override
    public String get(String key, Object... args) {
        return get(defaultLocale, key, args);
    }

    @Override
    public String get(Player player, String key, Object... args) {
        return get(new Locale(player.locale), key, args);
    }

    @Override
    public String get(Locale locale, String key, Object... args) {
        ResourceBundle bundle = getResourceBundle(locale);
        return Strings.format(bundle.getString(key), args);
    }

    private ResourceBundle getResourceBundle(Locale locale) {
        // Получаем файл для локали, переданной в качестве аргумента, если такового нет - используем стандартную локаль
        try {
            return ResourceBundle.getBundle("bundles/bundle", locale);
        } catch (MissingResourceException e) {
            return defaultBundle;
        }
    }
}
