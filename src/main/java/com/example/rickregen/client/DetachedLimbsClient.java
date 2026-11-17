package com.example.rickregen.client;

public class DetachedLimbsClient {

    // Paleta exacta de 3 colores neón de Rick Prime
    public static int getPrimeCableColor(int index) {
        int pattern = index % 3;
        switch (pattern) {
            case 0:
                return 0x00D2FF; // Azul eléctrico (#00D2FF)
            case 1:
                return 0xFF2222; // Rojo láser (#FF2222)
            default:
                return 0x00FF66; // Verde cibernético (#00FF66)
        }
    }

    // Al explotar un Creeper, las extremidades salen volando sin cables
    public static boolean shouldSeverCablesOnExplosion(boolean creeperExploded) {
        return creeperExploded;
    }
}