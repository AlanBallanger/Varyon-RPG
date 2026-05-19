package fr.varyon.vrpg.rpg;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum Profession {
    MINEUR     ("mineur",     "Mineur",     true,  null,        0, "Tool_Pickaxe_Adamantite"),
    FERMIER    ("fermier",    "Fermier",    true,  null,        0, "Tool_Sickle_Iron"),
    FORESTIER  ("forestier",  "Forestier",  true,  null,        0, "Tool_Hatchet_Adamantite"),
    CHASSEUR   ("chasseur",   "Chasseur",   true,  null,        0, "Food_Wildmeat_Raw"),
    FORGERON   ("forgeron",   "Forgeron",   false, "mineur",    15, "Tool_Hammer_Iron"),
    ALCHIMISTE ("alchimiste", "Alchimiste", false, "fermier",   15, "Potion_Regen_Mana"),
    ARTISAN    ("artisan",    "Artisan",    false, "forestier", 15, "Utility_Leather_Backpack"),
    CUISINIER  ("cuisinier",  "Cuisinier",  false, "chasseur",  15, "Food_Pie_Pumpkin");

    private final String id;
    private final String displayName;
    private final boolean base;
    private final String prereqId;
    private final int prereqLevel;
    private final String iconItemId;

    Profession(@Nonnull String id,
               @Nonnull String displayName,
               boolean base,
               @Nullable String prereqId,
               int prereqLevel,
               @Nonnull String iconItemId) {
        this.id = id;
        this.displayName = displayName;
        this.base = base;
        this.prereqId = prereqId;
        this.prereqLevel = prereqLevel;
        this.iconItemId = iconItemId;
    }

    @Nonnull public String getId()          { return id; }
    @Nonnull public String getDisplayName() { return displayName; }
    @Nonnull public String getIconItemId()  { return iconItemId; }
    public long getDebounceMs() {
        return switch (this) {
            case MINEUR     -> 3000L;
            case FERMIER    -> 1500L;
            case FORESTIER  -> 3000L;
            case CHASSEUR   -> 2000L;
            default         -> 1000L;
        };
    }
    @Nonnull public String getIconPath() {
        return switch (this) {
            case MINEUR     -> "Icons/ItemsGenerated/Tool_Pickaxe_Crude.png";
            case FERMIER    -> "Icons/ItemsGenerated/Tool_Sickle_Crude.png";
            case FORESTIER  -> "Icons/ItemsGenerated/Tool_Hatchet_Crude.png";
            case CHASSEUR   -> "Icons/ItemsGenerated/Food_Wildmeat_Raw.png";
            case FORGERON   -> "Icons/ItemsGenerated/Tool_Hammer_Crude.png";
            case ALCHIMISTE -> "Icons/ItemsGenerated/Potion_Regen_Mana_Small.png";
            case ARTISAN    -> "Icons/ItemsGenerated/Utility_Leather_Backpack.png";
            case CUISINIER  -> "Icons/ItemsGenerated/Food_Pie_Pumpkin.png";
        };
    }
    public boolean isBase()                  { return base; }
    public boolean isSpecialized()           { return !base; }
    public int getPrereqLevel()              { return prereqLevel; }

    @Nullable
    public Profession getPrereq() {
        return prereqId == null ? null : fromId(prereqId);
    }

    private static final Map<String, Profession> BY_ID =
        java.util.Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Profession::getId, p -> p));

    @Nullable
    public static Profession fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        if ("architecte".equals(id)) {
            id = "artisan";
        }
        return BY_ID.get(id);
    }

    @Nonnull
    public static List<Profession> bases() {
        return java.util.Arrays.stream(values())
            .filter(Profession::isBase)
            .toList();
    }

    @Nonnull
    public static List<Profession> specializations() {
        return java.util.Arrays.stream(values())
            .filter(Profession::isSpecialized)
            .toList();
    }
}
