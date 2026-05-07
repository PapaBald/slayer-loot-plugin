package com.papabald.slayerloot;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps a Slayer task name to the in-game NPC names that satisfy it.
 *
 * <p>Data mirrors {@code net.runelite.client.plugins.slayer.Task} from the
 * official Slayer plugin: each task entry stores any extra NPC names that
 * count toward the task in addition to the task's own (singular) name.
 *
 * <p>Matching follows the same convention as the Slayer plugin
 * ({@code SlayerPlugin#isTarget}): a whole-word, case-insensitive search of
 * the killed NPC's name against the task's targets and the task's singular
 * form.
 */
final class SlayerTaskTargets
{
    private static final Map<String, List<String>> TASK_ALIASES;

    static
    {
        Map<String, List<String>> m = new HashMap<>();
        // Pairs copied from the Slayer plugin's Task enum.
        // Key = task name (lowercased). Value = additional NPC names besides
        // the singular form of the task name.
        m.put("aberrant spectres", Arrays.asList("Spectre"));
        m.put("abyssal demons", Arrays.asList("Abyssal Sire"));
        m.put("araxytes", Arrays.asList("Araxxor"));
        m.put("aviansies", Arrays.asList("Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree"));
        m.put("bandits", Arrays.asList("Bandit", "Black Heather", "Donny the Lad", "Speedy Keith"));
        m.put("bats", Arrays.asList("Death wing"));
        m.put("bears", Arrays.asList("Callisto", "Artio"));
        m.put("birds", Arrays.asList("Chicken", "Rooster", "Terrorbird", "Seagull", "Vulture", "Duck", "Penguin", "Baby Roc"));
        m.put("black demons", Arrays.asList("Demonic gorilla", "Balfrug Kreeyath", "Skotizo", "Porazdir"));
        m.put("black knights", Arrays.asList("Black Knight"));
        m.put("blue dragons", Arrays.asList("Vorkath"));
        m.put("cave crawlers", Arrays.asList("Chasm crawler"));
        m.put("cave horrors", Arrays.asList("Cave abomination"));
        m.put("cave kraken", Arrays.asList("Kraken"));
        m.put("cockatrice", Arrays.asList("Cockathrice"));
        m.put("cows", Arrays.asList("Buffalo", "Brutus"));
        m.put("crabs", Arrays.asList("Ammonite Crab", "Frost Crab", "King Sand Crab", "Rock Crab", "Giant Rock Crab", "Sand Crab", "Swamp Crab"));
        m.put("crawling hands", Arrays.asList("Crushing hand"));
        m.put("custodian stalkers", Arrays.asList("Ancient Custodian"));
        m.put("dark beasts", Arrays.asList("Night beast"));
        m.put("dark warriors", Arrays.asList("Dark warrior"));
        m.put("dogs", Arrays.asList("Jackal", "Temple Guardian"));
        m.put("dust devils", Arrays.asList("Choke devil"));
        m.put("dwarves", Arrays.asList("Dwarf", "Black Guard"));
        m.put("elves", Arrays.asList("Elf", "Iorwerth Warrior", "Iorwerth Archer"));
        m.put("fire giants", Arrays.asList("Branda the Fire Queen"));
        m.put("fleshcrawlers", Arrays.asList("Flesh crawler"));
        m.put("fossil island wyverns", Arrays.asList("Ancient wyvern", "Long-tailed wyvern", "Spitting wyvern", "Taloned wyvern"));
        m.put("gargoyles", Arrays.asList("Dusk", "Dawn"));
        m.put("ghosts", Arrays.asList("Death wing", "Tortured soul", "Forgotten Soul", "Revenant"));
        m.put("goblins", Arrays.asList("Sergeant Strongstack", "Sergeant Grimspike", "Sergeant Steelwill"));
        m.put("greater demons", Arrays.asList("K'ril Tsutsaroth", "Tstanon Karlak", "Skotizo", "Tormented Demon"));
        m.put("green dragons", Arrays.asList("Elvarg"));
        m.put("the grotesque guardians", Arrays.asList("Dusk", "Dawn"));
        m.put("hellhounds", Arrays.asList("Cerberus"));
        m.put("hill giants", Arrays.asList("Cyclops", "Reanimated giant", "Obor"));
        m.put("ice giants", Arrays.asList("Eldric the Ice King"));
        m.put("ice warriors", Arrays.asList("Icelord"));
        m.put("infernal mages", Arrays.asList("Malevolent mage"));
        m.put("jellies", Arrays.asList("Jelly"));
        m.put("the cave kraken boss", Arrays.asList("Kraken"));
        m.put("lava dragons", Arrays.asList("Lava dragon"));
        m.put("lesser demons", Arrays.asList("Zakl'n Gritch"));
        m.put("lesser nagua", Arrays.asList("Sulphur Nagua", "Frost Nagua", "Amoxliatl"));
        m.put("lizardmen", Arrays.asList("Lizardman"));
        m.put("metal dragons", Arrays.asList("Bronze dragon", "Iron Dragon", "Steel dragon", "Mithril dragon", "Adamant dragon", "Rune dragon"));
        m.put("monkeys", Arrays.asList("Tortured gorilla", "Demonic gorilla", "Padulah"));
        m.put("moss giants", Arrays.asList("Bryophyta"));
        m.put("mutated zygomites", Arrays.asList("Zygomite", "Fungi"));
        m.put("nechryael", Arrays.asList("Nechryarch"));
        m.put("ogres", Arrays.asList("Enclave guard", "Mogre", "Ogress", "Skogre", "Zogre"));
        m.put("pirates", Arrays.asList("Pirate"));
        m.put("pyrefiends", Arrays.asList("Flaming pyrelord"));
        m.put("scabarites", Arrays.asList("Scarab swarm", "Locust rider", "Scarab mage", "Small Scarab"));
        m.put("scorpions", Arrays.asList("Scorpia", "Lobstrosity"));
        m.put("shades", Arrays.asList("Loar", "Phrin", "Riyl", "Asyn", "Fiyr", "Urium"));
        m.put("skeletons", Arrays.asList("Vet'ion", "Calvar'ion", "Skeletal Mystic"));
        m.put("spiders", Arrays.asList("Kalrag", "Sarachnis", "Venenatis", "Spindel", "Araxxor", "Araxyte"));
        m.put("spiritual creatures", Arrays.asList("Spiritual ranger", "Spiritual mage", "Spiritual warrior"));
        m.put("trolls", Arrays.asList("Dad", "Arrg", "Stick", "Kraka", "Pee Hat", "Rock", "Twig", "Berry"));
        m.put("tzhaar", Arrays.asList("TzTok-Jad", "TzKal-Zuk"));
        m.put("vampyres", Arrays.asList("Vyrewatch"));
        m.put("warped creatures", Arrays.asList("Warped terrorbird", "Warped tortoise", "Mutated terrorbird", "Mutated tortoise"));
        m.put("werewolves", Arrays.asList("Werewolf"));
        m.put("wolves", Arrays.asList("Wolf"));
        m.put("wyrms", Arrays.asList("Wyrmling", "Strykewyrm"));
        m.put("zombies", Arrays.asList("Undead", "Vorkath", "Zogre"));

        TASK_ALIASES = Collections.unmodifiableMap(m);
    }

    private SlayerTaskTargets()
    {
    }

    /**
     * @return {@code true} if {@code npcName} satisfies the task named
     * {@code taskName}. Matching is case-insensitive and whole-word, exactly
     * matching the official Slayer plugin's behavior.
     */
    static boolean matches(String taskName, String npcName)
    {
        if (taskName == null || npcName == null)
        {
            return false;
        }

        String task = taskName.trim().toLowerCase(Locale.ROOT);
        String npc = npcName.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
        if (task.isEmpty() || npc.isEmpty())
        {
            return false;
        }

        // Singular form of the task name (e.g. "dogs" -> "dog") is always a
        // valid target — same rule the Slayer plugin applies in
        // rebuildTargetNames() with replaceAll("s$", "").
        if (wholeWordMatch(npc, task.replaceAll("s$", "")))
        {
            return true;
        }

        List<String> aliases = TASK_ALIASES.get(task);
        if (aliases != null)
        {
            for (String alias : aliases)
            {
                if (wholeWordMatch(npc, alias.toLowerCase(Locale.ROOT)))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean wholeWordMatch(String haystack, String needle)
    {
        if (needle == null || needle.isEmpty())
        {
            return false;
        }

        // Same boundary semantics as SlayerPlugin#targetNamePattern:
        // (?:\s|^)<target>(?:\s|$), case-insensitive.
        String regex = "(?:\\s|^)" + Pattern.quote(needle) + "(?:\\s|$)";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(haystack).find();
    }
}
