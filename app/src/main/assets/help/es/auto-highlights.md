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
- **Marcador de subida:** Se activa en la pendiente de subida elegida, con velocidad mínima opcional.
- **Marcador de bajada:** Se activa en la pendiente de bajada elegida, con velocidad mínima opcional.
- **Marcadores de inicio/fin de ruta:** Marca el inicio y el final de la ruta solo si la cámara ya está grabando. Pausar y reanudar no añade marcadores. Nunca inicia una grabación.

Solo se utilizan los activadores que habilites en Ajustes.

## Umbrales de subida y bajada

Activa subida, bajada o ambas por separado. Usa una pendiente positiva para subidas y negativa para bajadas. Desactiva **Exigir también velocidad** para usar solo la pendiente. Si lo activas, deben cumplirse ambos límites.

Por ejemplo, usa **+10%** sin condición de velocidad para marcar una subida empinada a cualquier ritmo. Usa **+5%** y **30 km/h** para una subida rápida. Lo mismo se aplica a las bajadas con pendiente negativa. Las pequeñas fluctuaciones no crean destacados repetidos.

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
