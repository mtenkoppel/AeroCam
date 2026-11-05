# Como funcionam os destaques automáticos

## O que é um destaque?

Um destaque marca um momento interessante numa gravação de vídeo. Ajuda-te a encontrá-lo mais tarde sem rever a gravação inteira.

O AeroCam pode adicionar um destaque quando acontece algo durante a tua volta. O evento que o provoca chama-se **acionador**.

## Acionadores disponíveis

- **Marcador de volta:** Começa uma nova volta.
- **Pico de potência:** A tua potência ultrapassa o limite definido.
- **Velocidade máxima:** A tua velocidade ultrapassa o limite definido.
- **Topo de subida:** O Karoo indica o fim de uma subida.
- **Pico de frequência cardíaca:** A tua frequência cardíaca ultrapassa o limite definido.
- **Marcador de subida:** Ativa-se na inclinação de subida escolhida, com velocidade mínima opcional.
- **Marcador de descida:** Ativa-se na inclinação de descida escolhida, com velocidade mínima opcional.
- **Marcadores de início/fim da volta:** Marca o início e o fim da volta apenas se a câmara já estiver a gravar. Pausar e retomar não adiciona marcadores. Nunca inicia uma gravação.

Só são utilizados os acionadores que ativares nas definições.

## Limites para subidas e descidas

Ativa subida, descida ou ambas separadamente. Usa inclinação positiva nas subidas e negativa nas descidas. Desativa **Exigir também velocidade** para usar apenas a inclinação. Com a opção ativa, ambos os limites têm de ser atingidos.

Por exemplo, **+10%** sem condição de velocidade marca uma subida íngreme a qualquer ritmo. **+5%** e **30 km/h** marcam uma subida rápida. O mesmo vale para descidas com inclinação negativa. Pequenas oscilações não criam destaques repetidos.

## Durante uma gravação

Quando ocorre um acionador durante uma gravação, o AeroCam envia à câmara um comando para adicionar um destaque. Os campos de dados Video e Cam Control mostram brevemente o acionador detetado.

## Início automático da gravação

Normalmente, os destaques automáticos só são adicionados quando já existe uma gravação em curso.

Ativa **Iniciar gravação automaticamente** se quiseres que um acionador inicie uma gravação quando a câmara não está a gravar.

O primeiro acionador inicia a gravação, mas **não** é adicionado como destaque. Os acionadores seguintes podem adicionar destaques a essa gravação.

Isto mantém os comandos da câmara numa ordem segura e evita enviar Iniciar gravação e Adicionar destaque quase ao mesmo tempo.

## Escolher os limites

Escolhe valores que correspondam a momentos que queiras encontrar novamente. Se for demasiado fácil atingir um limite, a gravação pode acumular demasiados destaques.

Começa com um valor exigente e ajusta-o depois de algumas voltas.
