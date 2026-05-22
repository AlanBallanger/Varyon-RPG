package fr.varyon.vrpg.profession.fermier;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FarmerAnimalTable {

    public static final String GUARDIAN_CROP_ROLE = "cow_undead";
    public static final String GUARDIAN_CROP_DROP = "Plant_Seeds_Ghost";

    public static final long BASE_ANIMAL_KILL_XP = 30L;
    public static final long BASE_MILK_XP = 15L;

    private static final Set<String> MILKABLE_KEYWORDS = Set.of("cow");

    private static final Map<String, String> ANIMAL_DROPS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(GUARDIAN_CROP_ROLE, GUARDIAN_CROP_DROP);
        m.put("cow",    "Food_Beef_Raw");
        m.put("sheep",  "Food_Wildmeat_Raw");
        m.put("chicken","Food_Chicken_Raw");
        m.put("pig",    "Food_Pork_Raw");
        m.put("goat",   "Food_Wildmeat_Raw");
        m.put("boar",   "Food_Wildmeat_Raw");
        m.put("turkey", "Food_Wildmeat_Raw");
        ANIMAL_DROPS = m;
    }

    private FarmerAnimalTable() {}

    @Nullable
    public static String getDropItem(String roleLower) {
        return ANIMAL_DROPS.get(roleLower);
    }

    @Nullable
    public static String resolveDropItemContains(String roleLower) {
        String exact = ANIMAL_DROPS.get(roleLower);
        if (exact != null) return exact;
        for (Map.Entry<String, String> e : ANIMAL_DROPS.entrySet()) {
            if (roleLower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    public static boolean isMilkableAnimal(String roleLower) {
        return MILKABLE_KEYWORDS.stream().anyMatch(roleLower::contains);
    }
}
