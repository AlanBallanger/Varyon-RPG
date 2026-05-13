package fr.varyon.vrpg.rpg;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum Profession {
    MINEUR     ("mineur",     "Mineur",     true,  null,     0),
    FERMIER    ("fermier",    "Fermier",    true,  null,     0),
    FORESTIER  ("forestier",  "Forestier",  true,  null,     0),
    CHASSEUR   ("chasseur",   "Chasseur",   true,  null,     0),
    FORGERON   ("forgeron",   "Forgeron",   false, "mineur",    15),
    ALCHIMISTE ("alchimiste", "Alchimiste", false, "fermier",   15),
    ARCHITECTE ("architecte", "Architecte", false, "forestier", 15),
    CUISINIER  ("cuisinier",  "Cuisinier",  false, "chasseur",  15);

    private final String id;
    private final String displayName;
    private final boolean base;
    private final String prereqId;
    private final int prereqLevel;

    Profession(@Nonnull String id,
               @Nonnull String displayName,
               boolean base,
               @Nullable String prereqId,
               int prereqLevel) {
        this.id = id;
        this.displayName = displayName;
        this.base = base;
        this.prereqId = prereqId;
        this.prereqLevel = prereqLevel;
    }

    @Nonnull public String getId()          { return id; }
    @Nonnull public String getDisplayName() { return displayName; }
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
        return id == null ? null : BY_ID.get(id);
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
