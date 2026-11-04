# Come funzionano gli highlight automatici

## Che cos’è un highlight?

Un highlight segna un momento interessante all’interno di una registrazione video. Ti aiuta a ritrovarlo in seguito senza dover rivedere l’intera registrazione.

AeroCam può aggiungere un highlight quando accade qualcosa durante l’uscita. L’evento che lo provoca si chiama **attivatore**.

## Marcatori di inizio/fine uscita

Questa opzione è disattivata nelle nuove installazioni. Gli aggiornamenti conservano le scelte esistenti e il precedente valore predefinito delle installazioni meno recenti. Attiva **Highlight automatici** e **Marcatori di inizio/fine uscita** per utilizzarla.

- Il marcatore iniziale viene aggiunto solo se la videocamera sta già registrando alla partenza. Avviare la registrazione più tardi non aggiunge un marcatore iniziale retroattivo.
- Il marcatore finale viene aggiunto quando termina un’uscita attiva o in pausa, se la videocamera sta ancora registrando. Karoo segnala lo stesso stato finale quando si salva o si scarta un’uscita; entrambe le azioni possono quindi creare il marcatore.
- Pause e riprese non aggiungono marcatori. I marcatori mancati non vengono recuperati in seguito, neppure dopo una riconnessione.
- Questi marcatori non avviano mai una registrazione, anche se l’avvio automatico è attivo, e non sono soggetti all’intervallo minimo dei marcatori automatici a soglia.


## Attivatori disponibili

- **Marcatore giro:** Inizia un nuovo giro.
- **Picco di potenza:** La potenza supera la soglia impostata.
- **Velocità massima:** La velocità supera la soglia impostata.
- **Cima della salita:** Il Karoo segnala la fine di una salita.
- **Picco di frequenza cardiaca:** La frequenza cardiaca supera la soglia impostata.
- **Marcatore salita:** Vengono raggiunte sia la velocità in salita sia la pendenza positiva impostate.
- **Marcatore discesa:** Vengono raggiunte sia la velocità in discesa sia la pendenza negativa impostate.
- **Marcatori di inizio/fine uscita:** Segna l’inizio e la fine dell’uscita solo se la videocamera sta già registrando. Pause e riprese non aggiungono marcatori. Non avvia mai una registrazione.

Vengono usati solo gli attivatori abilitati nelle impostazioni.

## Soglie per salita e discesa

Abilita la salita, la discesa o entrambe in modo indipendente. Ogni direzione ha soglie proprie di velocità e pendenza, e **devono essere raggiunte entrambe le soglie**.

Usa una **pendenza positiva per la salita** e una **negativa per la discesa**. Ad esempio, in salita **20 km/h** e **+5%** significano almeno 20 km/h su una salita con pendenza del 5% o superiore. In discesa **60 km/h** e **−6%** significano almeno 60 km/h su una discesa con pendenza del 6% o superiore.

I valori esistenti per la discesa rimangono **50 km/h** e **−3%**; l’attivatore rimane disabilitato finché non lo abiliti. Anche il nuovo attivatore di salita è inizialmente disabilitato, con **20 km/h** e **+3%** come valori iniziali. Piccole oscillazioni intorno a una soglia non generano highlight ripetuti.

## Durante una registrazione

Quando si verifica un attivatore durante una registrazione, AeroCam invia alla fotocamera il comando per aggiungere un highlight. I campi dati Video e Cam Control mostrano brevemente l’attivatore rilevato.

## Avvio automatico della registrazione

Normalmente gli highlight automatici vengono aggiunti solo quando una registrazione è già in corso.

Abilita **Avvio automatico registrazione** se vuoi che un attivatore avvii una registrazione quando la fotocamera non sta registrando.

Il primo attivatore avvia la registrazione, ma **non** viene aggiunto come highlight. Gli attivatori successivi possono aggiungere highlight a quella registrazione.

Questo mantiene i comandi della fotocamera in un ordine sicuro ed evita di inviare Avvia registrazione e Aggiungi highlight quasi contemporaneamente.

## Scegliere le soglie

Scegli valori che corrispondano a momenti che vuoi ritrovare. Una soglia troppo facile da raggiungere può generare troppi highlight.

Parti da un valore impegnativo e adattalo dopo alcune uscite.
