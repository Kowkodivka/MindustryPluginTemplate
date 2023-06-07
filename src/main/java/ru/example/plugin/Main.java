package ru.example.plugin;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.mod.Plugin;
import mindustry.world.blocks.storage.CoreBlock;
import ru.example.plugin.bundles.DefaultBundler;

import static mindustry.game.EventType.BuildSelectEvent;
import static mindustry.net.Administration.ActionType;

public class Main extends Plugin {
    private static final DefaultBundler bundler = new DefaultBundler();

    // Вызывается, когда игра инициализируется
    @Override
    public void init() {
        Events.on(BuildSelectEvent.class, event -> {
            // Проверяем план строительства единцы на наличие ториевого реактора
            if (!event.breaking && event.builder != null && event.builder.buildPlan() != null && event.builder.buildPlan().block == Blocks.thoriumReactor) {
                // Оповещаем игроков, если условие выполняется
                Groups.player.forEach(player -> {
                    // Проходимся по всем игрокам и отправляем каждому локализованное сообщение
                    Unit builder = event.builder;
                    if (builder.isPlayer()) {
                        player.sendMessage(bundler.get(player, "events.thorium-reactor.player", builder.getPlayer().name));
                    } else {
                        player.sendMessage(bundler.get(player, "events.thorium-reactor.unit", builder.type.name));
                    }
                });
            }
        });

        // Добавляем фильтр чата, который изменяет все входящие сообщения
        // В данном случае все сообщения, содержащие "блять", будут под цензурой
        Vars.netServer.admins.addChatFilter((player, text) -> text.replace("блять", "❤❤❤❤❤"));

        // Добавляем фильтр на действия игроков, чтобы запретить делать некоторые вещи
        // В данном случае запрещаем игроку класть взрывчатую смесь в ядро
        Vars.netServer.admins.addActionFilter(action -> {
            // Лямбда всегда должна возвращать булево значение
            if ((action.type == ActionType.depositItem) && (action.item == Items.blastCompound) && (action.tile.block() instanceof CoreBlock)) {
                action.player.sendMessage(bundler.get(action.player, "filters.blast-compound"));
                // Запрещаем
                return false;
            }

            // Разрешаем
            return true;
        });

        // Здесь мы используя таймер, проходимся по всем ядрам и вокруг создаем эффект
        // Выполняется каждые 2 секунды
        Timer.schedule(() -> {
            int radius = 5;
            int numPoints = 360;

            for (int x = 0; x < Vars.world.width(); x++) {
                for (int y = 0; y < Vars.world.height(); y++) {
                    // Проходимся и ищем все ядра на карте
                    // Так же надо убедиться, что мы нашли центр ядра
                    if ((Vars.world.tile(x, y).block() instanceof CoreBlock) && Vars.world.tile(x, y).isCenter()) {
                        // Рисуем круг
                        for (int i = 0; i < numPoints; i++) {
                            double angle = 2.0 * Math.PI * i / numPoints;
                            // Также надо не забыть нормализовать координаты умножением на Vars.tilesize
                            float targetX = (float) (x + radius * Math.cos(angle)) * Vars.tilesize;
                            float targetY = (float) (y + radius * Math.sin(angle)) * Vars.tilesize;
                            Call.effect(Fx.absorb, targetX, targetY, Mathf.random((int) angle), Color.scarlet);
                        }
                    }
                }
            }
        }, 1, 2);
    }

    // Регистрирует команды, которые выполняются на сервере
    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("reactors", bundler.get("commands.reactors.description"), args -> {
            for (int x = 0; x < Vars.world.width(); x++) {
                for (int y = 0; y < Vars.world.height(); y++) {
                    // Проходимся и логируем все реакторы на карте
                    // Так же надо убедиться, что мы нашли центр реактора
                    if (Vars.world.tile(x, y).block() == Blocks.thoriumReactor && Vars.world.tile(x, y).isCenter()) {
                        Log.info("Reactor at @, @", x, y);
                    }
                }
            }
        });
    }

    // Регистрирует команды, которые игроки могут выполнять находясь в игре
    @Override
    public void registerClientCommands(CommandHandler handler) {
        // Регистрирует простую команду, которая повторяет в чат (нам лично) сообщения
        handler.<Player>register("reply", bundler.get("commands.reply.params"), bundler.get("commands.reply.description"), (args, player) -> player.sendMessage(bundler.get(player, "commands.reply.said", args[0])));

        // Регистрирует команду, которая отправляет личные сообщения другому игроку
        handler.<Player>register("whisper", bundler.get("commands.whisper.params"), bundler.get("commands.whisper.description"), (args, player) -> {
            // Ищем игрока на сервере по нику
            Player other = Groups.player.find(target -> target.name.equalsIgnoreCase(args[0]));

            // Отправляем сообщение с цветом scarlet, если игрок не найден
            if (other == null) {
                player.sendMessage(bundler.get("commands.whisper.not-found", args[0]));
                return;
            }

            // Отправляем сообщение другому игроку, используя [lightgray] для серого цвета текста и [] для сброса цвета
            other.sendMessage(bundler.get(other, "commands.whisper.message", player.name, args[1]));
        });
    }
}