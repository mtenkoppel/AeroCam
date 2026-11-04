# Como funcionam os destaques automáticos

## O que é um destaque?

Um destaque marca um momento interessante numa gravação de vídeo. Ajuda-te a encontrá-lo mais tarde sem rever a gravação inteira.

O AeroCam pode adicionar um destaque quando acontece algo durante a tua volta. O evento que o provoca chama-se **acionador**.

## Marcadores de início/fim da volta

Esta opção está desativada em novas instalações. As atualizações preservam as escolhas existentes e o valor predefinido anterior das instalações antigas. Ative **Destaques automáticos** e **Marcadores de início/fim da volta** para a utilizar.

- Só é adicionado um marcador de início se a câmara já estiver a gravar no início da volta. Começar a gravar mais tarde não adiciona um marcador de início retroativo.
- É adicionado um marcador de fim ao terminar uma volta ativa ou em pausa, se a câmara ainda estiver a gravar. O Karoo comunica o mesmo estado final ao guardar ou descartar a volta; ambas as ações podem gerar esse marcador.
- Pausar e retomar não adiciona marcadores. Os marcadores perdidos não são recuperados mais tarde, nem após uma nova ligação.
- Estes marcadores nunca iniciam uma gravação, mesmo com o início automático ativo, e não estão sujeitos ao intervalo mínimo dos marcadores automáticos por limiar.


## Acionadores disponíveis

- **Marcador de volta:** Começa uma nova volta.
- **Pico de potência:** A tua potência ultrapassa o limite definido.
- **Velocidade máxima:** A tua velocidade ultrapassa o limite definido.
- **Topo de subida:** O Karoo indica o fim de uma subida.
- **Pico de frequência cardíaca:** A tua frequência cardíaca ultrapassa o limite definido.
- **Marcador de subida:** São atingidos os limites definidos de velocidade de subida e de inclinação positiva.
- **Marcador de descida:** São atingidos os limites definidos de velocidade de descida e de inclinação negativa.
- **Marcadores de início/fim da volta:** Marca o início e o fim da volta apenas se a câmara já estiver a gravar. Pausar e retomar não adiciona marcadores. Nunca inicia uma gravação.

Só são utilizados os acionadores que ativares nas definições.

## Limites para subidas e descidas

Ativa a subida, a descida ou ambas de forma independente. Cada direção tem os seus próprios limites de velocidade e inclinação, e **ambos os limites têm de ser atingidos**.

Usa uma **inclinação positiva para subidas** e uma **negativa para descidas**. Por exemplo, numa subida, **20 km/h** e **+5%** significam pelo menos 20 km/h numa inclinação ascendente de 5% ou superior. Numa descida, **60 km/h** e **−6%** significam pelo menos 60 km/h numa inclinação descendente de 6% ou superior.

Os valores de descida existentes continuam a ser **50 km/h** e **−3%**; o acionador permanece desativado até o ativares. O novo acionador de subida também começa desativado, com **20 km/h** e **+3%** como valores iniciais. Pequenas oscilações em torno de um limite não criam destaques repetidos.

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
