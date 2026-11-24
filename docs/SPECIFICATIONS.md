# Nocturnal Sound Logger — Spécifications v0.1

App Android qui enregistre le niveau sonore ambiant toute la nuit, détecte les pics, et présente le lendemain un historique complet, consultable et exportable. Objectif : pallier les limitations de Decibel X (historique de nuit non consultable).

## 1. Objectif et cas d'usage

- Le téléphone (Android) reste **branché** près du lit toute la nuit.
- L'utilisateur lance une session « nuit » (manuellement ou programmée, ex. 23h → 7h).
- Au réveil, il consulte la courbe du niveau sonore de la nuit, avec les pics et événements identifiés.
- Les nuits précédentes restent archivées et comparables.

## 2. Acquisition

### 2.1 Mesure

- Source : micro du téléphone via `AudioRecord` (PCM brut), **pas** l'API `MediaRecorder.getMaxAmplitude()` (résolution trop grossière).
- Calcul du **Leq équivalent** (niveau RMS) par fenêtre, converti en dB SPL **calibré** :
  - calibration par défaut (offset approximatif du micro) réglable finement par l'utilisateur avec un sonomètre de référence si besoin ;
  - valeur affichée : dB(A) si possible (pondération A appliquée en logiciel via filtres IEC 61672), sinon dB FS + offset.
- Plage cible : 25 à 110 dB ; résolution 0,1 dB.

### 2.2 Fréquence d'échantillonnage des niveaux

| Paramètre | Valeur recommandée | Justification |
|---|---|---|
| Fenêtre d'enregistrement (hop) | **250 ms** | Bon compromis latence/volume |
| Échantillons audio bruts | 16 kHz, mono, 16 bits | suffisant pour du niveau sonore |
| Niveaux stockés (raw) | 1 point / 250 ms | 115 200 points / nuit suffit largement |
| Données par nuit | ≈ 300 Ko (CSV) ou ≈ 80 Ko (binaire) | négligeable |

- Nuits de 8 h : à 250 ms → 115 200 lignes ; à 500 ms → 57 600 ; à 100 ms → 288 000 (encore acceptable, mais inutile pour de la mesure de niveau).
- **Décision : hop de 250 ms par défaut, réglable 100–1000 ms dans les paramètres.** Aucun vrai besoin de descendre sous 100 ms : un événement sonore dure typiquement > 1 s.
- Chaque point : timestamp epoch_ms, Leq_fenêtre (dB), dB_min et dB_max dans la fenêtre.

### 2.3 Exécution en arrière-plan (contrainte Android clé)

- **Foreground service** avec `microphone` comme `foregroundServiceType` (Android 10+) → micro actif même écran verrouillé.
- Notification permanente obligatoire (compteur temps, niveau courant optionnel, contrôle pause/stop).
- Permissions : `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (Android 13+), `RECEIVE_BOOT_COMPLETED` (relance auto du service programmé après reboot).
- Ignore l'optimisation de batterie pendant une session (app et téléphone sont branchés, pas de contrainte énergétique).
- `setKeepScreenOn` non requis ; verrouillage d'écran OK.
- **Wake lock partiel** pendant la session pour éviter que le CPU dorme entre les fenêtres.
- Reprise sur incident : si le service est tué, redémarrage automatique et marquage de la discontinuité dans les données.

## 3. Traitement — détection de pics et événements

- Après la nuit (ou en fin de fenêtre), agrégation :
  - par minute : Leq_min, min, max, L10, L50, L90 ;
  - par heure : idem.
- **Détection d'événements** — implémente les deux modes paramétrés dans l'interface (cf. 4.1) :
  1. **Seuil absolu** : événement dès que le niveau dépasse le seuil fixe pendant ≥ durée minimale (défaut 500 ms).
  2. **Seuil relatif différé (défaut)** : calcul de la référence = moyenne des niveaux entre t0+15 min et t0+30 min ; événement quand le niveau dépasse référence + ΔdB (défaut +10 dB) pendant ≥ 500 ms.
  - Fin d'événement après retour sous le seuil de fin (seuil de référence − 2 dB, ou seuil absolu − 2 dB) pendant ≥ 10 s.
  - Tant que la référence n'est pas calculée (avant t0+30 min), la détection en mode relatif est suspendue et signalée dans le rapport.
  - Événement = début, fin, durée, Leq de l'événement, dB max, écart au bruit moyen de la nuit, courbe zoomable de l'événement.
  - Un événement encore en cours à la fin de la session est persisté **tronqué** (marqué `truncated`, fin = heure d'arrêt).
- Classification heuristique optionnelle (motif spectral simple) : pic bref (claquement), soutenu (conversation/TV), grondement (trafic), siren-like (modulation) — affichée comme « type probable », jamais comme vérité.
- Marquage des discontinuités (données manquantes).

## 4. Interface

### 4.1 Écran principal
- Grand bouton **Démarrer / Arrêter la nuit**.
- Niveau courant (dB) en temps réel, indicateur d'enregistrement.
- Rappel de la session programmée si active.
- **Paramétrage des événements** (modal dédiée). Deux modes de détection au choix :
  1. **Seuil absolu** : événement dès que le niveau dépasse un seuil fixe déterminé par l'utilisateur (ex. 55 dB), réglable par slider + saisie fine.
  2. **Seuil relatif différé** : événement si le niveau dépasse de +10 dB (ajustable) la moyenne des niveaux mesurés entre **t0+15 min et t0+30 min** (t0 = début de session), fenêtre servant de « référence du calme » en début de nuit, réglable.
  - Paramètres communs : durée minimale de dépassement (défaut 500 ms), durée de retour au calme (défaut 10 s).
  - Mode par défaut : seuil relatif différé. Un indicateur montre la référence mesurée (moyenne t0+15→t0+30) une fois calculée.

### 4.2 Rapport du matin
- Courbe du Leq sur toute la nuit (glisser/zoomer, min→max par bandes) :
  - couleur par plage (vert < 45, orange 45–60, rouge > 60 dB) ;
  - marqueurs sur les événements détectés.
- Tapping un marqueur → détail de l'événement (heure, durée, max, courbe zoomée).
- Statistiques de la nuit :
  - Leq global, min/max, L10/L50/L90, heure la plus bruyante ;
  - **liste des événements** : heure de début, niveau sonore moyen de l'événement (Leq de l'événement), durée, et **écart au bruit moyen de la nuit** (ex. « 18 dB au-dessus de la moyenne de la nuit ») ;
  - comparaison du Leq de la nuit avec la moyenne des nuits précédentes.

### 4.3 Historique multi-nuits
- Liste des nuits (date, Leq, nb d'événements, mini-sparkline).
- **Résumé des différentes nuits** : tableau une ligne par nuit avec date, Leq de la nuit, Leq moyen de référence, **nombre d'événements de chaque nuit** (total, et réparti par type si classification activée), durée de la session.
- Suppression / renommage d'une nuit.

### 4.4 Paramètres
- Heures auto start/stop, fréquence d'échantillonnage (100–1000 ms), seuils de détection (assistés par un tutoriel guidé), calibration du micro, thème sombre (par défaut).

## 5. Stockage et export

- Base locale (Room/SQLite) : table `nights` (id, début, fin, Leq, stats JSON), `samples` (night_id, ts, leq, min, max) ou fichier binaire par nuit + index.
- Rétention : suppression auto optionnelle au-delà de N nuits (défaut : illimité, ~80 Ko/nuit).
- **Export par nuit** : CSV (timestamp ISO-8601, dB) + export des statistiques ; partage via intent Android (mail, Drive, etc.).
- Nom de fichier : `night_YYYY-MM-DD.csv`.
- Pas de cloud obligatoire ; tout est local (argument privacy).

## 6. Non-buts (v1)

- Enregistrement audio du contenu (on ne garde que les niveaux, pas les sons).
- Streaming/remote monitoring.
- iOS.
- Analyse fréquentielle avancée (spectrogramme).

## 7. Choix techniques indicatifs

- Kotlin, Jetpack Compose, MVVM.
- `AudioRecord` 16 kHz/16 bits mono, fenêtre 250 ms, calcul RMS + pondération A.
- Foreground service (type `microphone`), WorkManager pour sessions programmées.
- Room + export CSV ; bibliothèque de tracé : Vico ou MPAndroidChart.
- Tests : mesure de nuit simulée (fichier WAV injecté), test service tué/redémarré, test volume de données.

## 8. Critères d'acceptation

1. Une session de 8 h sans interruption produit 28 800+ points exploitables, aucune donnée manquante non détectée.
2. Verrouiller l'écran pendant la nuit n'interrompt pas la mesure.
3. Le rapport du matin est consultable en < 2 s après la fin de session.
4. Un événement testé (ex. claquement de porte) est détecté et horodaté à ±1 s.
5. Export CSV d'une nuit réimportable dans un tableur sans nettoyage manuel.
6. 30 nuits archivées occupent < 5 Mo.
