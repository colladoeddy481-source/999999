# Rick Prime Regeneration - Minecraft 1.20.1 (Fabric + Forge)

Comandos: /regen (inicia la animacion) y /regen reset (devuelve tu skin).
Tambien se activa solo con un golpe mortal (max. 12 veces, ver RegenLogic.MAX_DEATH_REGENS).

Estructura:
- common/ codigo compartido (logica, comando, datos de red, mixins) + textura
- fabric/ entrada de Fabric
- forge/  entrada de Forge (ForgeGradle 6 + MixinGradle)

Skin: reemplaza common/src/main/resources/assets/rickregen/textures/entity/rick_prime.png
por tu skin 64x64 (brazos anchos).

Obtener los .jar sin instalar nada (GitHub Actions):
 1. Crea un repositorio en GitHub y sube TODO el contenido de esta carpeta (incluida la carpeta oculta .github).
 2. Pestana "Actions" > ejecucion "Compilar mod" > espera el check verde (5-10 min).
 3. Abajo, en "Artifacts", descarga "jars": dentro estan el jar de forge y el de fabric.
 4. Pon en tu carpeta mods el jar de tu loader.
Si falla, abre el paso que fallo y copia el error.
