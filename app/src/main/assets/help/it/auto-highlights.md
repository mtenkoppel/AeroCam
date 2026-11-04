# Come funzionano gli highlight automatici

## Che cos’è un highlight?

Un highlight segna un momento interessante all’interno di una registrazione video. Ti aiuta a ritrovarlo in seguito senza dover rivedere l’intera registrazione.

AeroCam può aggiungere un highlight quando accade qualcosa durante l’uscita. L’evento che lo provoca si chiama **attivatore**.

## Attivatori disponibili

- **Marcatore giro:** Inizia un nuovo giro.
- **Picco di potenza:** La potenza supera la soglia impostata.
- **Velocità massima:** La velocità supera la soglia impostata.
- **Cima della salita:** Il Karoo segnala la fine di una salita.
- **Picco di frequenza cardiaca:** La frequenza cardiaca supera la soglia impostata.
- **Marcatore salita:** Si attiva alla pendenza di salita scelta, con velocità minima facoltativa.
- **Marcatore discesa:** Si attiva alla pendenza di discesa scelta, con velocità minima facoltativa.
- **Marcatori di inizio/fine uscita:** Segna l’inizio e la fine dell’uscita solo se la videocamera sta già registrando. Pause e riprese non aggiungono marcatori. Non avvia mai una registrazione.

Vengono usati solo gli attivatori abilitati nelle impostazioni.

## Soglie per salita e discesa

Attiva salita, discesa o entrambe separatamente. Usa una pendenza positiva per le salite e negativa per le discese. Disattiva **Richiedi anche la velocità** per usare solo la pendenza. Se attivo, entrambi i limiti devono essere raggiunti.

Per esempio, **+10%** senza condizione di velocità marca una salita ripida a qualsiasi ritmo. **+5%** e **30 km/h** marcano una salita veloce. Lo stesso vale per le discese con pendenza negativa. Le piccole oscillazioni non creano highlight ripetuti.

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
