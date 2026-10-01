package io.github.nidaba.skyforge.world;

/** One backend-neutral geological interpretation of an authored island interior. */
public record SkyIslandGeologySample(
        boolean owned,
        double bulkCompetence,
        double fractureIntensity,
        double connectedPermeability,
        double groundwaterPotential,
        double voidFormationPotential) {

    public SkyIslandGeologySample {
        requireNormalized("bulkCompetence", bulkCompetence);
        requireNormalized("fractureIntensity", fractureIntensity);
        requireNormalized("connectedPermeability", connectedPermeability);
        requireNormalized("groundwaterPotential", groundwaterPotential);
        requireNormalized("voidFormationPotential", voidFormationPotential);
        if (!owned
                && (bulkCompetence != 0.0
                        || fractureIntensity != 0.0
                        || connectedPermeability != 0.0
                        || groundwaterPotential != 0.0
                        || voidFormationPotential != 0.0)) {
            throw new IllegalArgumentException("unowned geology samples must contain only zero potentials");
        }
    }

    public static SkyIslandGeologySample outside() {
        return new SkyIslandGeologySample(false, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
