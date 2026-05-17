package fr.varyon.vrpg.profession.fermier;

import javax.annotation.Nullable;
import java.util.Map;

public final class FarmerAnimalTable {

    private static final Map<String, String> ANIMAL_DROPS = Map.of(
        "cow",     "Food_Beef_Raw",
        "sheep",   "Food_Wildmeat_Raw",
        "chicken", "Food_Chicken_Raw",
        "pig",     "Food_Pork_Raw",
        "goat",    "Food_Wildmeat_Raw",
        "boar",    "Food_Wildmeat_Raw",
        "turkey",  "Food_Wildmeat_Raw"
    );

    private FarmerAnimalTable() {}

    @Nullable
    public static String getDropItem(String roleLower) {
        return ANIMAL_DROPS.get(roleLower);
    }
}
