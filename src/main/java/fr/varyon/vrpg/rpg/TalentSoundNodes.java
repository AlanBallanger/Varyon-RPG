package fr.varyon.vrpg.rpg;

import javax.annotation.Nonnull;

public final class TalentSoundNodes {

    private TalentSoundNodes() {}

    @Nonnull
    public static String lootDoubleNode(@Nonnull Profession profession) {
        return switch (profession) {
            case MINEUR -> "0";
            case FERMIER -> "0";
            case FORESTIER -> "0";
            case CHASSEUR -> "4";
            default -> "";
        };
    }

    @Nonnull
    public static String fantomatiqueNode(@Nonnull Profession profession) {
        return switch (profession) {
            case MINEUR -> "2";
            case FERMIER -> "3";
            case FORESTIER -> "2";
            case CHASSEUR -> "2";
            default -> "";
        };
    }

    @Nonnull
    public static String comboNode(@Nonnull Profession profession) {
        return switch (profession) {
            case MINEUR -> "5";
            case FERMIER -> "6";
            case FORESTIER -> "5";
            case CHASSEUR -> "5";
            default -> "";
        };
    }

    public static boolean hasSoundToggle(@Nonnull Profession profession, @Nonnull String nodeId) {
        if (nodeId.isEmpty()) return false;
        if (nodeId.equals(lootDoubleNode(profession))
            || nodeId.equals(fantomatiqueNode(profession))
            || nodeId.equals(comboNode(profession))) {
            return !lootDoubleNode(profession).isEmpty();
        }
        if (profession == Profession.MINEUR && ("4".equals(nodeId) || "8".equals(nodeId) || "9".equals(nodeId))) {
            return true;
        }
        if (profession == Profession.FORESTIER && ("10".equals(nodeId) || "12".equals(nodeId))) {
            return true;
        }
        if (profession == Profession.CHASSEUR && "11".equals(nodeId)) {
            return true;
        }
        return profession == Profession.FERMIER && ("4".equals(nodeId) || "5".equals(nodeId) || "9".equals(nodeId) || "11".equals(nodeId));
    }
}
