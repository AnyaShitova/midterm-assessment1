package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));
        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            System.out.println("Память: used=" + used + " free=" + free + " total=" + total);
        });
        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));
        this.commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите направление (north, south, east, west).");
            }
            String direction = a.get(0);
            Room nextRoom = ctx.getCurrent().getNeighbors().get(direction);
            if (nextRoom == null) {
                throw new InvalidCommandException("Нет пути в направлении: " + direction);
            }
            ctx.setCurrent(nextRoom);
            System.out.println("Вы перешли в: " + nextRoom.getName());
            System.out.println(nextRoom.describe());
        });
        this.commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета.");
            }
            String itemName = String.join(" ", a);
            Room currentRoom = ctx.getCurrent();
            Item item = currentRoom.getItems().stream()
                    .filter(i -> i.getName().equalsIgnoreCase(itemName))
                    .findFirst()
                    .orElseThrow(() -> new InvalidCommandException("Предмет не найден: " + itemName));
            currentRoom.getItems().remove(item);
            ctx.getPlayer().getInventory().add(item);
            System.out.println("Взято: " + item.getName());
        });
        this.commands.put("inventory", (ctx, a) -> {
            Map<String, List<Item>> groupedItems = ctx.getPlayer().getInventory().stream()
                    .collect(Collectors.groupingBy(i -> i.getClass().getSimpleName()));
            groupedItems.forEach((type, items) -> {
                System.out.println("- " + type + " (" + items.size() + "): " + items.get(0).getName());
            });
        });
        this.commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета.");
            }
            String itemName = String.join(" ", a);
            Player player = ctx.getPlayer();
            Item item = player.getInventory().stream()
                    .filter(i -> i.getName().equalsIgnoreCase(itemName))
                    .findFirst()
                    .orElseThrow(() -> new InvalidCommandException("Предмет не найден в инвентаре: " + itemName));
            item.apply(ctx);
        });
        this.commands.put("fight", (ctx, a) -> {
            Room currentRoom = ctx.getCurrent();
            Monster monster = currentRoom.getMonster();
            if (monster == null) {
                throw new InvalidCommandException("В комнате нет монстра.");
            }
            Player player = ctx.getPlayer();
            while (player.getHp() > 0 && monster.getHp() > 0) {
                // Игрок атакует
                int playerDamage = player.getAttack();
                monster.setHp(monster.getHp() - playerDamage);
                System.out.println("Вы бьёте " + monster.getName() + " на " + playerDamage + ". HP монстра: " + monster.getHp());
                if (monster.getHp() <= 0) {
                    break;
                }
                // Монстр атакует
                int monsterDamage = monster.getLevel(); // Простая логика атаки монстра
                player.setHp(player.getHp() - monsterDamage);
                System.out.println("Монстр отвечает на " + monsterDamage + ". Ваше HP: " + player.getHp());
            }
            if (player.getHp() <= 0) {
                System.out.println("Вы погибли!");
                System.exit(0);
            } else {
                System.out.println("Монстр побеждён!");
                currentRoom.setMonster(null);
            }
        });
        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        cave.getNeighbors().put("west", forest);

        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.setMonster(new Monster("Волк", 1, 8));

        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\s+"));
                String cmd = parts.get(0).toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}
