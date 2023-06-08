package ru.example.plugin;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.mod.Plugin;
import mindustry.net.NetConnection;
import mindustry.world.blocks.storage.CoreBlock;
import ru.example.plugin.bundles.DefaultBundler;

import java.util.Objects;

import static mindustry.game.EventType.*;
import static mindustry.net.Administration.ActionType;
import static mindustry.net.Administration.PlayerInfo;
import static mindustry.net.Packets.Connect;

public class Main extends Plugin {
    private static final int maxIdenticalIPs = 3;
    private static final DefaultBundler bundler = new DefaultBundler();

    // Вызывается, когда игра инициализируется
    @Override
    public void init() {
        // Событие, которое происходит во время взрыва реактора неоплазии
        Events.on(GeneratorPressureExplodeEvent.class, event -> Core.app.post(() -> {
            // Проверяем, достаточно ли ресурсов для создания данного типа единицы и не находится ли она в черном списке
            if (!Units.canCreate(event.build.team, UnitTypes.renale)) return;

            // Создаем эффект и единицу типа renale
            Call.spawnEffect(event.build.x, event.build.y, 0f, UnitTypes.renale);
            UnitTypes.renale.spawn(event.build.team, event.build);
        }));

        // Событие, которое происходит во время строительства блока
        Events.on(BuildSelectEvent.class, event -> {
            // Проверяем план строительства единицы на наличие ториевого реактора
            // Оповещаем игроков, если условие выполняется
            if (!event.breaking && event.builder != null && event.builder.buildPlan() != null && event.builder.buildPlan().block == Blocks.thoriumReactor)
                Groups.player.forEach(player -> {
                    // Проходимся по всем игрокам и отправляем каждому локализованное сообщение
                    Unit builder = event.builder;
                    if (builder.isPlayer())
                        player.sendMessage(bundler.get(player, "events.thorium-reactor.player", builder.getPlayer().name, event.tile.x, event.tile.y));
                    else
                        player.sendMessage(bundler.get(player, "events.thorium-reactor.unit", builder.type.name, event.tile.x, event.tile.y));
                });
        });

        // Добавляем фильтр чата, который изменяет все входящие сообщения
        // В данном случае все сообщения, содержащие "блять", будут под цензурой
        Vars.netServer.admins.addChatFilter((player, text) -> text.replace("блять", "блин"));

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

        // Переопределяем слушатель входящих пакетов на свой
        // В данном случае мы пишем простейшую защиту от ботов
        // Если соединений от одного ip больше, чем задано нашей переменной, то мы все соединения закрываем и заносим в черный список
        Vars.net.handleServer(Connect.class, (con, packet) -> {
            // Не забываем, что мы перезаписываем слушатель и надо вызвать событие, чтобы все остальные части кода, подписанные на него, работали корректно
            Events.fire(new ConnectionEvent(con));

            // Получаем соединения и ищем все те, которые связаны с входящим
            Seq<NetConnection> connections = Seq.with(Vars.net.getConnections()).filter(other -> other.address.equals(con.address));
            if (connections.size >= maxIdenticalIPs) {
                // Блокируем и отключаем их
                Vars.netServer.admins.blacklistDos(con.address);
                connections.each(NetConnection::close);
            }
        });

        // Здесь мы используя таймер, проходимся по всем ядрам и вокруг создаем эффект
        // Выполняется каждые 2 секунды
        Timer.schedule(() -> {
            int radius = 5;
            int numPoints = 360;

            // Проходимся и ищем все ядра на карте
            // Так же надо убедиться, что мы нашли центр ядра
            for (int x = 0; x < Vars.world.width(); x++)
                for (int y = 0; y < Vars.world.height(); y++)
                    // Рисуем круг
                    if ((Vars.world.tile(x, y).block() instanceof CoreBlock) && Vars.world.tile(x, y).isCenter())
                        for (int i = 0; i < numPoints; i++) {
                            double angle = 2.0 * Math.PI * i / numPoints;
                            // Также надо не забыть нормализовать координаты умножением на Vars.tilesize
                            float targetX = (float) (x + radius * Math.cos(angle)) * Vars.tilesize;
                            float targetY = (float) (y + radius * Math.sin(angle)) * Vars.tilesize;
                            Call.effect(Fx.absorb, targetX, targetY, Mathf.random((int) angle), Color.scarlet);
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
                    if (Vars.world.tile(x, y).block() == Blocks.thoriumReactor && Vars.world.tile(x, y).isCenter())
                        Log.info("Reactor at @, @", x, y);
                }
            }
        });
    }

    // Регистрирует команды, которые игроки могут выполнять находясь в игре
    @Override
    public void registerClientCommands(CommandHandler handler) {
        // Регистрируем простую команду, которая повторяет в чат (нам лично) сообщения
        handler.<Player>register("reply", bundler.get("commands.reply.params"), bundler.get("commands.reply.description"), (args, player) -> player.sendMessage(bundler.get(player, "commands.reply.said", args[0])));

        // Регистрируем команду, которая отправляет личные сообщения другому игроку
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

        handler.<Player>register("info", bundler.get("commands.info.params"), bundler.get("commands.info.description"), (args, player) -> {
            // PlayerInfo - содержит в себе информацию об игроке, который когда-то был на сервере
            PlayerInfo info = null;
            ObjectSet<PlayerInfo> found;

            // Проверяем первый параметр на наличие name или ip, чтобы нам было легче искать
            if (Objects.equals(args[0], "name")) {
                found = Vars.netServer.admins.findByName(args[1]);
                if (!found.isEmpty()) {
                    info = found.first();
                }
            } else if (Objects.equals(args[0], "ip")) {
                info = Vars.netServer.admins.findByIP(args[1]);
            } else {
                player.sendMessage(bundler.get(player, "commands.info.invalid-params", args[0]));
                return;
            }

            if (info == null) {
                player.sendMessage(bundler.get(player, "commands.info.not-found", args[1]));
                return;
            }

            String names = String.join("[], ", info.names);
            String ips = String.join("[], ", info.ips);

            // Проверяем игрока на наличие роли администратора и отправляем соответствующее сообщение
            if (player.admin)
                player.sendMessage(bundler.get(player, "commands.info.about.admin", info.lastName, info.banned, names, ips, info.timesJoined, info.timesKicked));
            else
                player.sendMessage(bundler.get(player, "commands.info.about", info.lastName, info.banned, names, info.timesJoined, info.timesKicked));
        });
    }
}