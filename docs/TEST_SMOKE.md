# Procédure de smoke test (ROADMAP étape 1 — acquisition de confiance)

Objectif : valider sur un **téléphone réel** (ou émulateur) qu'une session de nuit
écrit bien les samples en continu, écran verrouillé, avec notification permanente
et wake lock actif.

## Prérequis

- Téléphone en mode développeur, débogage USB actif, branché (chargeur).
- `adb` disponible. APK debug installé : `./gradlew assembleDebug` puis
  `adb install app/build/outputs/apk/debug/app-debug.apk`.
- Accorder les permissions runtime (le build debug est debuggable) :
  ```sh
  adb shell pm grant com.polo.soundlogger android.permission.RECORD_AUDIO
  adb shell pm grant com.polo.soundlogger android.permission.POST_NOTIFICATIONS
  ```

## Déroulement

1. Ouvrir l'app → écran « Nuit », appuyer sur **Démarrer la nuit**
   (ou, équivalent adb : `adb shell am start-foreground-service -n com.polo.soundlogger/.service.RecordingService`).
2. Vérifier la notification permanente :
   `adb shell dumpsys notification | grep -A3 soundlogger`
3. Verrouiller l'écran : `adb shell input keyevent KEYCODE_POWER`
   (ne pas toucher au téléphone, il reste branché).
4. Vérifier le service et le wake lock :
   ```sh
   adb shell dumpsys activity services | grep -i record
   adb shell dumpsys power | grep -i soundlogger
   ```
5. Laisser tourner **≥ 30 min**. Pendant la session, contrôler que les samples
   s'écrivent en continu (build debug, `run-as` autorisé) :
   ```sh
   adb shell run-as com.polo.soundlogger sqlite3 soundlogger.db \
     "SELECT COUNT(*), MIN(ts), MAX(ts) FROM Sample;"
   adb shell run-as com.polo.soundlogger sqlite3 soundlogger.db \
     "SELECT COUNT(*) FROM (SELECT ts, LAG(ts) OVER (ORDER BY ts) AS prev FROM Sample) WHERE ts - prev > 1000;"
   ```
   Le 2ᵉ query compte les discontinuités > 1 s : doit être 0 (critère d'acceptation 1).
6. Vérifier l'exemption Doze (optionnel, cf. réglage) :
   `adb shell dumpsys deviceidle | grep soundlogger`
7. Arrêter : `adb shell am stopservice -n com.polo.soundlogger/.service.RecordingService`
   ou bouton **Arrêter la nuit**, puis vérifier la nuit clôturée :
   `adb shell run-as com.polo.soundlogger sqlite3 soundlogger.db "SELECT id, startTime, endTime FROM Night;"`

## Critères de succès

- `COUNT(*)` ≈ durée_en_secondes × 1000 / 250 (fenêtre par défaut), aucune
  discontinuité > 1 s non marquée.
- Service toujours vivant après verrouillage de l'écran (critère 2).
- Notification permanente visible, wake lock `SoundLogger:night` held.
- Niveau courant cohérent (bruit ambiant typique d'une chambre : 25–45 dB(A)).

## Note calibration (étape 1)

Placer le téléphone à côté d'un sonomètre de référence, lire les deux valeurs,
ajuster **Réglages → Calibration micro** pour les faire coïncider (spec §2.1).
