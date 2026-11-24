# ROADMAP — SoundLogger

Étapes par ordre de priorité. Commiter et pousser après chacune.

## Étape 0 — Build initial (en cours)
- [ ] Cloner le dépôt, ouvrir dans Android Studio, ajouter le Gradle wrapper (`gradle wrapper --gradle-version 8.7`).
- [ ] `./gradlew assembleDebug` : corriger jusqu'à compilation OK (import manquants éventuels, ex. `androidx.core:core-ktx` pour NotificationCompat, `androidx.compose.material:material-icons-extended` pour les icônes de navigation).

## Étape 1 — Acquisition de confiance
- [ ] Smoke test sur téléphone réel : démarrer une session, verrouiller l'écran, vérifier que les samples s'écrivent en continu pendant ≥ 30 min.
- [ ] Vérifier notification permanente + immunité Doze (wake lock + éventuellement demande d'ignorer l'optimisation de batterie).
- [ ] Calibration : exposer `calibrationOffsetDb` dans les réglages + aide.
- [ ] Pondération A logicielle (filtres IEC 61672) — sinon rester en dB FS + offset avec affichage honnête.

## Étape 2 — Détection d'événements complète
- [ ] Compléter `EventDetector` : accumuler Leq/min/max pendant l'événement, Leq moyen, écart au bruit moyen de la nuit (TODO/NOTE v0.2 dans le fichier).
- [ ] Persister les événements (nouvelle table Room `events`).
- [ ] Tests unitaires sur nuits simulées.

## Étape 3 — Rapport du matin
- [ ] Courbe Leq de la nuit : Vico ou MPAndroidChart, zoom, couleur par plage (<45 vert, 45-60 orange, >60 rouge), marqueurs d'événements.
- [ ] Stats : Leq, min/max, L10/L50/L90, heure la plus bruyante.
- [ ] Liste des événements tapables → détail (courbe zoomée de l'événement).
- [ ] Comparaison du Leq avec la moyenne des nuits précédentes.

## Étape 4 — Historique et export
- [ ] Résumé multi-nuits (date, Leq, nb d'événements, durée, sparkline), suppression/renommage.
- [ ] FileProvider (`file_paths.xml` + déclaration manifest) pour le partage CSV.
- [ ] Déclencher l'agrégation et la détection à la fin de session (rétro-analyse de la nuit complète).

## Étape 5 — Sessions programmées et finitions
- [ ] Auto start/stop programmés (WorkManager), relance après reboot (RECEIVE_BOOT_COMPLETED).
- [ ] Modal de paramétrage des événements sur l'écran principal (spec §4.1 : slider seuil absolu, ΔdB, fenêtre de référence, durées).
- [ ] Suppression auto optionnelle au-delà de N nuits.

## Étape 6 — Release v1
- [ ] Vérifier les 6 critères d'acceptation (docs/SPECIFICATIONS.md §8).
- [ ] APK signé, tag v0.1.0.
