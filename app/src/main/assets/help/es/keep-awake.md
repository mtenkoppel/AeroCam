# Cómo funciona Mantener activa

## Por qué existe

AeroCam no puede encender de forma fiable una DJI Osmo Action que se ha apagado por completo.

**Mantener activa** evita esta limitación. Envía periódicamente una pulsación del botón QS para reiniciar el temporizador de apagado automático. La cámara permanece conectada y preparada aunque sus pantallas estén apagadas.

> **Batería:** Mantener activa consume mucha más batería que un modo de espera real.

## Configurar la cámara

Mantener activa funciona mejor con estos ajustes:

1. Ajusta **Single Screen Preview** a **Screen off when locked** para apagar las pantallas sin desconectar la cámara.
2. Ajusta **Auto Power Off** a un mínimo de **2 minutos**.
3. Conecta la cámara al Karoo y bloquea su pantalla. Esto apaga las pantallas y ahorra algo de energía.
4. Desactiva los sonidos si no quieres oír un clic con cada comando QS.

Los nombres de los menús pueden variar ligeramente según el firmware.

## Durante una ruta

Activa Mantener activa desde Control de cámara. AeroCam envía un comando QS inmediatamente y lo repite con el intervalo configurado.

Si se pierde la conexión, AeroCam reanuda la función después de volver a conectarse. Espera brevemente para que la cámara esté preparada.

Mantener activa se pausa durante una grabación, porque el mismo comando QS crea un destacado. Se reanuda cuando termina la grabación.

## Uso de batería

En una prueba, la batería bajó del **100 % al 64 % en aproximadamente 1 hora y 56 minutos**. Es solo una referencia práctica: la temperatura, las grabaciones, las pantallas y el estado de la batería afectan al resultado.

> **Importante:** Mantener activa evita el apagado. No puede encender una cámara que ya está totalmente apagada.
