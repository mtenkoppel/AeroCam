# Cómo funcionan los destacados automáticos

## ¿Qué es un destacado?

Un destacado marca un momento interesante dentro de una grabación de vídeo. Te ayuda a encontrarlo después sin tener que revisar toda la grabación.

AeroCam puede añadir un destacado cuando ocurre algo durante tu salida. El evento que lo provoca se llama **activador**.

## Activadores disponibles

- **Marcador de vuelta:** Comienza una nueva vuelta.
- **Pico de potencia:** Tu potencia supera el umbral configurado.
- **Velocidad máxima:** Tu velocidad supera el umbral configurado.
- **Cima de subida:** El Karoo indica el final de una subida.
- **Pico de frecuencia cardíaca:** Tu frecuencia cardíaca supera el umbral configurado.
- **Marcador de subida:** Se alcanzan tanto la velocidad de subida como la pendiente positiva configuradas.
- **Marcador de bajada:** Se alcanzan tanto la velocidad de bajada como la pendiente negativa configuradas.
- **Marcadores de inicio y fin:** Tu salida comienza o termina.

Solo se utilizan los activadores que habilites en Ajustes.

## Umbrales de subida y bajada

Activa la subida, la bajada o ambas de forma independiente. Cada dirección tiene sus propios umbrales de velocidad y pendiente, y **deben alcanzarse ambos umbrales**.

Usa una **pendiente positiva para la subida** y una **negativa para la bajada**. Por ejemplo, **20 km/h** y **+5%** en subida significan al menos 20 km/h en una pendiente ascendente del 5% o más. En bajada, **60 km/h** y **−6%** significan al menos 60 km/h en una pendiente descendente del 6% o más.

Los valores de bajada existentes siguen siendo **50 km/h** y **−3%**; el activador permanece desactivado hasta que lo habilites. El nuevo activador de subida también comienza desactivado, con **20 km/h** y **+3%** como valores iniciales. Las pequeñas fluctuaciones alrededor de un umbral no generan destacados repetidos.

## Con la grabación en curso

Si se produce un activador durante una grabación, AeroCam envía a la cámara una orden para añadir un destacado. Los campos de datos Video y Cam Control muestran brevemente qué activador se ha detectado.

## Inicio automático de la grabación

Normalmente, los destacados automáticos solo añaden destacados cuando ya hay una grabación en curso.

Activa **Iniciar grabación automáticamente** si quieres que un activador inicie una grabación cuando la cámara no está grabando.

El primer activador inicia la grabación, pero **no** se añade como destacado. Los activadores posteriores pueden añadir destacados a esa grabación.

Esto mantiene las órdenes de la cámara en un orden seguro y evita enviar Iniciar grabación y Añadir destacado casi al mismo tiempo.

## Elegir los umbrales

Elige valores que correspondan a momentos que quieras encontrar después. Si es demasiado fácil alcanzar un umbral, la grabación puede acumular demasiados destacados.

Empieza con un valor exigente y ajústalo después de algunas salidas.
