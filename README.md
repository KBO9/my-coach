# My Coach

Ton coach de poche pour la musculation et la sèche, pensé pour l'iPhone : le programme du coach, des séances guidées série par série, la voix du coach, la diète, le jeûne, les compléments, les progrès et les trophées.

**Ouvrir l'app : https://kbo9.github.io/my-coach/**

## Mettre en ligne (GitHub Pages, une seule fois)

1. Sur github.com : **+** → *New repository* → nom **my-coach** → *Public* → *Create repository*.
2. Dans le dépôt : *uploading an existing file* (ou *Add file → Upload files*) → sélectionne **tous les fichiers** de ce dossier → *Commit changes*.
3. **Settings → Pages** → *Deploy from a branch* → Branch **main**, dossier **/ (root)** → *Save*.
4. Une à deux minutes plus tard, l'app est en ligne sur **https://kbo9.github.io/my-coach/**.

Pour une mise à jour : refais l'étape 2 avec les nouveaux fichiers (ils remplacent les anciens). Tes données ne sont pas touchées.

## Installer sur le téléphone

- **iPhone (Safari)** : ouvre le lien, bouton Partager → « Sur l'écran d'accueil ».
- **Android (Chrome)** : menu ⋮ → « Installer l'application ».

Une fois installée, l'app s'ouvre en plein écran et fonctionne hors connexion (voix comprise).

## Ce que fait l'app

- **Séances guidées** : paliers, charge et reps suggérées, chrono de repos dans l'île dynamique, records détectés automatiquement.
- **Exercices à la carte** : ajoute, remplace ou retire un exo pendant la séance (machine prise…), modifie ton programme (ajouter, retirer, réordonner, revenir au programme du coach) et crée tes propres exercices. La bibliothèque ne propose que des variantes douces pour le dos.
- **Voix du coach** : une voix d'homme annonce le repos, les 10 dernières secondes, le décompte, la série ou l'exercice suivant (avec les reps et la charge visées), et toutes les phases du minuteur (fractionné, gainage, zone 2). Elle continue **écran verrouillé, écouteurs branchés**. Dans l'appli Android, c'est la voix naturelle du téléphone (Google ou Samsung), au choix.
- **Diète, jeûne, compléments, liste de courses**, suivi du poids (tendance lissée), mensurations, régularité et trophées.
- **Pas jour par jour** (Samsung Health) : objectif réglable, moyenne, série, record, graphique sur 14 jours et par semaine ; l'historique est gardé dans tes données.
- **Coach fondé sur les études** : dépense d'énergie (Mifflin-St Jeor × tes pas et tes séances), repères protéines/lipides, bilan toutes les 2 semaines sur ta vraie courbe de poids (±150 kcal proposées, garde-fou tour de taille, plancher), % de gras (formule US Navy), tour de taille/taille (NICE), taille/hanches (OMS), zone cardio selon l'âge (Tanaka), séance allégée quand un exercice stagne. Toutes les sources sont dans l'appli : Coach → Les bases scientifiques.

## Tes données

- **Enregistrement automatique** sur le téléphone, à chaque action.
- **Copie de secours** dans l'app : si le stockage principal est effacé, tout revient au démarrage.
- **Points de restauration** : un par jour (14 jours) et un avant chaque import ou remise à zéro (Réglages → Points de restauration).
- **Sauvegarde cloud (conseillée)** : une copie automatique dans le dépôt GitHub **privé** `my-coach-data`, quelques secondes après chaque changement. Nouveau téléphone : tu récupères tout en une minute.

### Activer la sauvegarde cloud (une seule fois)

0. Crée le dépôt **privé** `my-coach-data` : GitHub → **+** → *New repository* → *Private* → *Create repository*.
1. Sur GitHub : **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token** (ou le bouton « Ouvrir GitHub » dans l'app).
2. **Repository access** : *Only select repositories* → `my-coach-data`.
3. **Permissions** : *Contents* → *Read and write*.
4. **Generate token**, copie-le, puis dans l'app : **Réglages → Sauvegarde cloud → colle le jeton → Connecter**.

Le jeton reste uniquement sur le téléphone et ne donne accès qu'à ce dépôt. Chaque sauvegarde est une version : tu peux revenir en arrière depuis l'historique du dépôt.

Ce dépôt-ci (le code de l'app) est public, comme l'exige GitHub Pages gratuit : **aucune donnée personnelle n'y est enregistrée**.

## Appli Android (conseillée sur Android)

L'appli **My Coach pour Android** contient tout My Coach, plus ce qu'une page web ne sait pas faire :

- **Samsung Health** : sommeil (durée, phases), pas et pesées lus directement, via Health Connect. « Sommeil 7 h+ » et « 8 000 pas » se cochent tout seuls.
- **Notifications** : « C'est reparti ! » à la fin de chaque repos, même écran verrouillé, et rappels de séance et de pesée à l'heure choisie.
- **Voix naturelle** : le coach parle avec les voix de synthèse du téléphone (Google ou Samsung), bien plus naturelles. Ta musique baisse le temps de la phrase au lieu de s'arrêter. Choix de la voix : Réglages → Voix du coach → Voix.
- **Toujours à jour** : l'interface se charge depuis ce site, les améliorations arrivent toutes seules.

Installation :

1. Télécharge **my-coach.apk** depuis [Releases → My Coach pour Android](https://github.com/KBO9/my-coach/releases/tag/android) et installe-le (autorise l'installation depuis ton navigateur si Android le demande). Si My Coach Santé est installée, elle est simplement mise à jour.
2. Ouvre My Coach : tes données reviennent toutes seules depuis ta sauvegarde GitHub (sinon : Réglages → Sauvegarde cloud).
3. Autorise les notifications, puis Samsung Health (Réglages → Samsung Health) si ce n'est pas déjà fait.
4. Dans Samsung Health : **Paramètres → Health Connect** → autorise le sommeil, les pas et le poids.

L'APK est compilé automatiquement par GitHub à partir du dossier `android/`.

## Voix

**Appli Android** : la voix est celle de la synthèse vocale du téléphone (Google ou Samsung). Pour d'autres voix : Paramètres Android → Gestion globale → Synthèse vocale → moteur → installer les données vocales (français).

**Navigateur (iPhone)** : voix générée hors ligne avec [Piper](https://github.com/rhasspy/piper) et le modèle français « tom » ([Tjiho](https://git.bksp.space/Tjiho/French-tts-model-piper), licence AGPL-3.0). Chaque minuteur est assemblé en une piste audio : c'est ce qui permet à la voix de continuer quand l'iPhone est verrouillé. Pendant un repos, une musique lancée depuis une autre appli se met en pause (option « Garder ma musique » dans les réglages si ton iPhone la propose).

## Fichiers

| Fichier | Rôle |
|---|---|
| `index.html` | L'application complète |
| `voix-coach.bin`, `voix-coach.json` | Les phrases de la voix du coach |
| `manifest.webmanifest` | Nom, couleurs et icônes pour l'installation |
| `sw.js` | Mode hors connexion |
| `icon-*.png`, `apple-touch-icon.png` | Icônes de l'écran d'accueil |
| `android/` | Code de l'appli Android (compilée automatiquement par GitHub) |
