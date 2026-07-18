# Rapport de Triage des Issues FSCrawler

## Statistiques
- **Total d'issues ouvertes**: 97
- **Date de l'analyse**: 17 juillet 2026

---

## 🔴 Issues à Fermer (Déjà traitées ou obsolètes)

### #2174 - Provide auth details for ossindex when running from windows
**Statut**: Configuration Maven - Peut être fermée si corrigée dans le build  
**Action**: Vérifier si le problème ossindex persiste

### #1545 - Windows Docker compose
**Statut**: Bug confirmé - Devrait être résolu ou documenté  
**Complexité**: 3/10

### #1024 - Raw metadata not being populated for PDF documents
**Statut**: Documentation - Probablement résolu  
**Complexité**: 2/10 (doc seulement)

### #942 - FSCrawler won't crawl docx files in an Alpine linux container
**Statut**: Problème d'environnement Alpine - Documenter  
**Complexité**: 4/10

---

## 🟢 Issues Faciles (1-3/10)

### #331 - Add a test for continue_on_error option
**Complexité**: 1/10  
**Type**: Test unitaire  
**Effort**: Ajouter un test simple pour une fonctionnalité existante

### #388 - Add real integration test
**Complexité**: 3/10  
**Type**: Test d'intégration  
**Effort**: Infrastructure de test déjà en place avec TestContainers

### #1602 - Document gets indexed incorrectly in .doc
**Complexité**: 2/10  
**Type**: Bug Tika - Vérifier si résolu dans versions récentes  
**Statut**: À reproduire

### #955 - File permissions with author and group name showing as numbers
**Complexité**: 2/10  
**Type**: Feature request - Formatage des permissions  
**Effort**: Mapping UID/GID vers noms

### #833 - Index settings for hidden files and folders
**Complexité**: 2/10  
**Type**: Feature - Ajouter un paramètre de configuration  
**Effort**: Simple flag dans la configuration

### #802 - How to remove \n and \t chars when using FS Crawler
**Complexité**: 2/10  
**Type**: Documentation ou feature  
**Effort**: Pipeline Elasticsearch ou configuration Tika

### #795 - Create an elasticsearch doc per paragraph
**Complexité**: 3/10  
**Type**: Question/Feature  
**Effort**: Nécessite parsing et split du contenu

### #823 - Recognize lines with strikethrough in PDF files
**Complexité**: 3/10  
**Type**: Feature Tika  
**Effort**: Dépend des capacités de Tika

### #262 - Add Upload simple interface
**Complexité**: 2/10  
**Type**: Feature REST API  
**Effort**: Interface simple d'upload déjà présente

---

## 🟡 Issues Moyennes (4-6/10)

### #1916 - Add support for password protected documents
**Complexité**: 5/10  
**Type**: Feature request  
**Effort**: Tika supporte les mots de passe via PasswordProvider. Nécessite:
- Ajout d'un paramètre `password` dans la config et REST API
- Implémentation du PasswordProvider
- Tests avec documents protégés

### #1867 - Fscrawler always overwrites existing documents
**Complexité**: 5/10  
**Type**: Feature - Upsert au lieu de overwrite  
**Effort**: Modifier la stratégie d'indexation Elasticsearch (doc_as_upsert)

### #1709 - REST Service file upload >20MB returns exception
**Complexité**: 4/10  
**Type**: Bug - Limite Jackson StreamReadConstraints  
**Effort**: Configurer StreamReadConstraints.builder().maxStringLength()

### #1693 - Fscrawler doesn't resume indexing
**Complexité**: 5/10  
**Type**: Bug  
**Effort**: Vérifier la persistance de l'état dans l'index .fscrawler

### #1659 - Support metadata files related to binary files
**Complexité**: 5/10  
**Type**: Feature - Metadata sidecar files  
**Effort**: 
- Nouveau paramètre `metadata_dir`
- Lecture de fichiers `.json` associés
- Merge avec métadonnées extraites

### #1626 - Add a built-in File HTTP Service
**Complexité**: 4/10  
**Type**: Feature - Serveur de fichiers HTTP  
**Effort**: Simple serveur HTTP statique pour démos

### #1549 - Manage jobs with the REST Service
**Complexité**: 6/10  
**Type**: Feature - API de gestion des jobs  
**Effort**: 
- CRUD des jobs via REST
- Gestion du cycle de vie (start/stop)
- Persistance de la configuration

### #1471 - Use file ctime, not mtime for detecting modified files
**Complexité**: 4/10  
**Type**: Feature request  
**Effort**: Option pour utiliser ctime au lieu de mtime

### #1432 - Add new setting `checksum_as_id`
**Complexité**: 4/10  
**Type**: Feature - Utiliser le checksum comme ID document  
**Effort**: Calculer hash du fichier et l'utiliser comme _id

### #1420 - Skip indexing of files within zip archive
**Complexité**: 4/10  
**Type**: Feature - Configuration pour ignorer le contenu des archives  
**Effort**: Ajouter un flag dans la config

### #1410 - Support image custom metadata (EXIF)
**Complexité**: 5/10  
**Type**: Feature - Extraction métadonnées EXIF  
**Effort**: Tika extrait déjà les métadonnées, à exposer

### #1334 - Async upload via REST
**Complexité**: 6/10  
**Type**: Feature - Upload asynchrone  
**Effort**: Queue de tâches, retour immédiat avec job ID

### #1315 - Add a `noop` output
**Complexité**: 4/10  
**Type**: Feature - Output noop pour tests  
**Effort**: Implémentation d'un DocumentService qui ne fait rien

### #1300 - fscrawler doesn't index new files
**Complexité**: 5/10  
**Type**: Bug confirmé  
**Effort**: Investigation du mécanisme de watch

### #1293 - Rest API ignores ocr.language settings
**Complexité**: 4/10  
**Type**: Bug  
**Effort**: Passer les settings OCR au parser Tika

### #1258 - Do not extract ALL raw metadata
**Complexité**: 5/10  
**Type**: Feature - Filtrage des métadonnées  
**Effort**: Configuration pour limiter les métadonnées extraites

### #1253 - documents.log to contain physical path of file
**Complexité**: 4/10  
**Type**: Feature - Logging amélioré  
**Effort**: Ajouter le path complet dans les logs

### #1230 - Auto detect file when moved into watched directory
**Complexité**: 6/10  
**Type**: Feature - File system watcher  
**Effort**: Nécessite WatchService (voir #399)

### #1097 - Duplication of PDF content when OCR is on
**Complexité**: 5/10  
**Type**: Bug  
**Effort**: Configurer correctement la stratégie OCR de Tika

### #1093 - Closing FS crawler / thread still running
**Complexité**: 5/10  
**Type**: Bug - Gestion du shutdown  
**Effort**: Corriger la fermeture propre des threads

### #987 - FSCrawler statistics in monitoring stack
**Complexité**: 6/10  
**Type**: Feature - Monitoring/metrics  
**Effort**: Exposer métriques Prometheus ou Elasticsearch monitoring

### #868 - Monitor fscrawler progress from logs or terminal
**Complexité**: 4/10  
**Type**: Feature - Progress reporting  
**Effort**: Logs structurés ou endpoint REST pour le statut

### #566 - Ingestion of >10MB single file fails
**Complexité**: 5/10  
**Type**: Bug/Config - Chunking de gros fichiers  
**Effort**: Configuration des limites ou streaming

### #474 - REST service hostname containing underscore yields error
**Complexité**: 4/10  
**Type**: Bug - Validation hostname  
**Effort**: Fix regex de validation

### #341 - Submit job settings via REST API
**Complexité**: 6/10  
**Type**: Feature - Liée à #1549  
**Effort**: API pour créer/modifier jobs

### #1584 - REST API chunk upload
**Complexité**: 6/10  
**Type**: Feature - Upload en chunks  
**Effort**: Support multipart/chunked upload

### #1456 - Can't connect to local elasticsearch
**Complexité**: 4/10  
**Type**: Bug à reproduire - Problème de connexion  
**Effort**: Investigation config SSL/auth

### #1429 - Timeout when indexing large folders
**Complexité**: 5/10  
**Type**: Bug - Gestion des timeouts  
**Effort**: Configuration des timeouts bulk

### #1421 - Small bugs
**Complexité**: 4/10  
**Type**: Bug multiple  
**Effort**: À détailler

### #1395 - Error crawling: inputstream is closed
**Complexité**: 5/10  
**Type**: Bug à reproduire  
**Effort**: Gestion des streams

### #1375 - Bulk request timeout 30s
**Complexité**: 4/10  
**Type**: Bug/Config - Timeout  
**Effort**: Configuration timeout augmenté

### #923 - Indexing subdirectory of remote URL throws error
**Complexité**: 5/10  
**Type**: Bug à reproduire  
**Effort**: Fix URL parsing

### #917 - Date Format options
**Complexité**: 4/10  
**Type**: Question/Feature  
**Effort**: Configuration format de date

### #904 - PDF file cannot be uploaded to elasticsearch
**Complexité**: 4/10  
**Type**: Bug à reproduire  
**Effort**: Investigation

### #890 - ArithmeticException: integer overflow while Crawling
**Complexité**: 5/10  
**Type**: Bug - Overflow  
**Effort**: Fix calcul

### #884 - Add additional tag while indexing local files
**Complexité**: 4/10  
**Type**: Feature - Tags personnalisés  
**Effort**: Configuration de tags additionnels

### #813 - Can't use loadbalancer URL
**Complexité**: 4/10  
**Type**: Bug à reproduire  
**Effort**: Support multi-nodes

### #800 - Hook to trigger additional commands after OCR
**Complexité**: 5/10  
**Type**: Feature - Post-processing hooks  
**Effort**: Système de hooks/callbacks

### #767 - Split documents per page
**Complexité**: 6/10  
**Type**: Feature - 1 doc ES par page  
**Effort**: Parser page par page

### #964 - Issue with part searching
**Complexité**: 4/10  
**Type**: Bug à reproduire  
**Effort**: Investigation

### #956 - File permissions not displaying as 664
**Complexité**: 4/10  
**Type**: Bug à reproduire  
**Effort**: Format permissions

### #943 - Crawling on recently created files in addition to periodic scan
**Complexité**: 6/10  
**Type**: Feature - Watch mode  
**Effort**: Lié à #399

### #941 - Issue with Memory Management
**Complexité**: 5/10  
**Type**: Bug - Mémoire  
**Effort**: Profiling et optimisation

### #931 - Windows GUI like DocFetcher
**Complexité**: 6/10  
**Type**: Feature - Interface graphique  
**Effort**: Développement d'une GUI complète

### #851 - Elasticsearch auto index documents in tmp/es directory
**Complexité**: 4/10  
**Type**: Question/Config  
**Effort**: Clarification docs

### #1496 - Where's the HOCR output
**Complexité**: 4/10  
**Type**: Question/Feature  
**Effort**: Documentation ou feature

### #1550 - Failed to start Grizzly: Address already in use
**Complexité**: 3/10  
**Type**: Bug/Config - Port occupé  
**Effort**: Gestion des erreurs de port

### #1566 - file.create index field has wrong value
**Complexité**: 4/10  
**Type**: Bug - Mapping date  
**Effort**: Fix extraction date

### #1704 - Dockerized samba does not produce results in spotlight macOS
**Complexité**: 5/10  
**Type**: Bug à reproduire - Spotlight  
**Effort**: Investigation Samba/macOS

### #2103 - Spotlight macOS not working with samba
**Complexité**: 5/10  
**Type**: Bug similaire à #1704  
**Effort**: Investigation

### #1605 - File ignored if bigger than ignore_above
**Complexité**: 4/10  
**Type**: Bug/Behavior - Ignorer silencieusement  
**Effort**: Log warning quand ignoré

---

## 🟠 Issues Complexes (7-8/10)

### #2222 - Add support for LLMs
**Complexité**: 8/10  
**Type**: Feature - Intégration LLM  
**Effort**: 
- Intégration avec LangChain4j
- Génération de résumés/tags
- Configuration API keys
- Coût d'exécution

### #2221 - Add MCP Service to allow indexing remotely
**Complexité**: 7/10  
**Type**: Feature - Model Context Protocol  
**Effort**: 
- Exposer FSCrawler comme service MCP
- Permettre aux agents LLM d'indexer des documents
- Nouveau protocole à implémenter

### #2255 - Generate a thumbnail version of document
**Complexité**: 7/10  
**Type**: Feature - Génération de thumbnails  
**Effort**: 
- Intégration API externe (Jina.ai)
- Ou bibliothèque Java locale
- Stockage des thumbnails

### #1297 - Generate thumbnails (duplicate de #2255)
**Complexité**: 7/10  
**Type**: Feature  
**Effort**: Même que #2255

### #2046 - Evaluate docling project
**Complexité**: 7/10  
**Type**: Feature - Nouveau parser de documents  
**Effort**: 
- Évaluation et intégration alternative à Tika
- Migration ou support parallèle

### #1824 - Add/document Support for OpenSearch
**Complexité**: 7/10  
**Type**: Feature/Compatibility  
**Effort**: 
- Tests avec OpenSearch 1.x et 2.x
- Client compatible
- Documentation
- CI/CD

### #1816 - Allow using external processor to process data
**Complexité**: 7/10  
**Type**: Feature - Plugin system  
**Effort**: 
- Architecture de plugins
- Intégration LangChain4j
- Génération d'embeddings

### #1776 - Create FSCrawler integration package for Elastic
**Complexité**: 7/10  
**Type**: Feature - Package Elastic  
**Effort**: 
- Suivre spec Elastic Package
- Monitoring intégré
- Dashboards Kibana

### #1745 - Add support for HEIC files
**Complexité**: 7/10  
**Type**: Feature - Format image Apple  
**Effort**: 
- Vérifier support Tika HEIC
- Conversion HEIC → JPEG
- Dépendances natives possibles

### #794 - Use External API for OCR (Amazon Textract, Google Vision)
**Complexité**: 8/10  
**Type**: Feature - OCR cloud  
**Effort**: 
- Intégration APIs multiples
- Gestion credentials
- Coût et rate limiting
- Fallback sur Tesseract

### #708 - Interface ABBYY FineReader OCR
**Complexité**: 8/10  
**Type**: Feature - OCR commercial  
**Effort**: Similaire à #794, API propriétaire

### #717 - Store configuration in .fscrawler-vX index
**Complexité**: 7/10  
**Type**: Feature - Configuration centralisée  
**Effort**: 
- Migration config fichier → ES
- API de gestion config
- Backwards compatibility

### #689 - Add a Web Crawler
**Complexité**: 8/10  
**Type**: Feature - Nouveau crawler type  
**Effort**: 
- Crawler HTTP/HTTPS
- Gestion robots.txt
- Extraction liens
- Respect rate limiting

### #627 - Support parallel crawling
**Complexité**: 7/10  
**Type**: Feature - Multi-threading  
**Effort**: 
- Architecture concurrent
- Thread pool
- Synchronisation état

### #399 - Use a WatchService implementation
**Complexité**: 7/10  
**Type**: Feature - File system events  
**Effort**: 
- Java WatchService
- Gestion événements FS
- Cross-platform (Linux/Windows/macOS)

### #377 - Add a Rsync implementation
**Complexité**: 7/10  
**Type**: Feature - Nouveau protocole  
**Effort**: 
- Client Rsync en Java
- Protocole différentiel

### #264 - Support Dropbox as FS Provider
**Complexité**: 7/10  
**Type**: Feature - Cloud storage  
**Effort**: 
- API Dropbox
- OAuth
- Similaire à S3

### #1003 - Provide hooks for custom processing
**Complexité**: 7/10  
**Type**: Feature - Plugin hooks  
**Effort**: Architecture extensible

### #998 - Ability to index full XML content as text
**Complexité**: 7/10  
**Type**: Feature - Parser XML amélioré  
**Effort**: Option de parsing XML

### #1000 - Add server name as field in doc
**Complexité**: 7/10  
**Type**: Feature - Metadata additionnelle  
**Effort**: Ajouter hostname/server info

---

## 🔴 Issues Très Complexes (9-10/10)

### #2306 - Add support for auth/auto (Keycloak)
**Complexité**: 9/10  
**Type**: Feature - Authentification OAuth/OIDC  
**Effort**: 
- Intégration Keycloak
- OAuth2/OIDC flows
- Gestion tokens
- Sécurité REST API

### #1922 - Issues with zip files
**Complexité**: 8/10  
**Type**: Bug - Traitement archives  
**Effort**: Investigation complexe des archives

### #1851 - Fix Github Actions for external contributors
**Complexité**: 8/10  
**Type**: CI/CD - Sécurité GitHub Actions  
**Effort**: Configuration secrets/permissions

### #682 - Add a beats output
**Complexité**: 9/10  
**Type**: Feature - Nouveau output  
**Effort**: 
- Protocole Beats
- Alternative à output ES
- Architecture multi-outputs

### #677 - Evaluate using ECS data structure
**Complexité**: 9/10  
**Type**: Breaking change - Elastic Common Schema  
**Effort**: 
- Migration structure documents
- Breaking change majeur
- Compatibilité backwards

### #966 - Switch build to JDK 14 (obsolète, maintenant Java 17+)
**Complexité**: 8/10  
**Type**: Update - Build system  
**Effort**: Migration Java versions

### #1588 - Switch to GraalVM
**Complexité**: 10/10  
**Type**: Feature - Native compilation  
**Effort**: 
- Migration vers GraalVM
- Native image
- Tests compatibilité
- Tika/JNI challenges

### #1587 - Replace jCommander by Picocli
**Complexité**: 8/10  
**Type**: Refactoring - CLI library  
**Effort**: Migration totale CLI

### #529 - Use VertX
**Complexité**: 10/10  
**Type**: Architecture - Reactive  
**Effort**: 
- Refonte complète architecture
- Migration vers reactive
- Breaking changes massifs

### #466 - ACL support through Samba
**Complexité**: 9/10  
**Type**: Feature - Security ACLs  
**Effort**: 
- Extraction ACLs Samba
- Mapping vers ES document-level security
- Complexité Windows/Unix

### #66 - Support for child doc types
**Complexité**: 9/10  
**Type**: Feature - Nested documents  
**Effort**: 
- Architecture parent-child ES
- Relations entre documents

### #25 - Support Asciidoc format
**Complexité**: 8/10  
**Type**: Feature - Nouveau format  
**Effort**: Parser Asciidoc via Tika ou custom

### #1335 - Kotlin contribution
**Complexité**: 8/10  
**Type**: Discussion - Langage  
**Effort**: Migration partielle vers Kotlin (non prioritaire)

---

## 📊 Résumé par Catégorie

### Par Complexité
- **Facile (1-3)**: 10 issues
- **Moyenne (4-6)**: 52 issues
- **Complexe (7-8)**: 23 issues
- **Très Complexe (9-10)**: 12 issues

### Par Type
- **Bug confirmé**: ~20 issues
- **Bug à reproduire**: ~15 issues
- **Feature request**: ~50 issues
- **Documentation**: ~5 issues
- **Test**: ~2 issues
- **Architecture/Refactoring**: ~5 issues

### Priorités Suggérées

#### 🔥 Haute Priorité (Impact utilisateur élevé)
1. #1709 - REST upload >20MB (Bug bloquant)
2. #1916 - Password protected documents (Demande fréquente)
3. #1867 - Upsert mode (Cas d'usage important)
4. #1693 - Resume indexing (Bug fonctionnel)
5. #1300 - Doesn't index new files (Bug critique)
6. #1097 - PDF duplication with OCR (Bug qualité données)

#### ⚡ Moyenne Priorité (Améliorations utiles)
1. #1659 - Metadata sidecar files (Flexibilité)
2. #1432 - Checksum as ID (Déduplication)
3. #1549 - Manage jobs via REST (UX)
4. #1258 - Filter raw metadata (Performance)
5. #987 - Monitoring stack (Ops)
6. #1334 - Async upload (Performance)

#### 🎯 Basse Priorité (Nice to have)
1. #1626 - Built-in HTTP server (Demo only)
2. #833 - Hidden files settings (Edge case)
3. #331 - Test continue_on_error (Quality)
4. #388 - Integration tests (Quality)

#### 🔮 Long Terme (Projets majeurs)
1. #2222 - LLM support (Innovation)
2. #2221 - MCP Service (Innovation)
3. #689 - Web Crawler (Nouveau scope)
4. #1824 - OpenSearch support (Compatibilité)
5. #399 - WatchService (Architecture)
6. #677 - ECS structure (Breaking change)
7. #1588 - GraalVM (Performance)

---

## 🎬 Actions Recommandées

### À Fermer Immédiatement
- #2174 (ossindex auth) - Vérifier si encore pertinent
- #1024 (raw metadata doc) - Vérifier si résolu
- #942 (Alpine docx) - Documenter limitation
- #262 (Upload interface) - Déjà existant?

### Quick Wins (2-4 semaines)
1. #331 - Ajouter test continue_on_error
2. #1709 - Fix Jackson string length limit
3. #833 - Hidden files config flag
4. #1550 - Meilleure gestion erreur port
5. #1605 - Log warning pour ignore_above

### Prochains Sprints (1-3 mois)
1. #1916 - Password protected documents
2. #1867 - Upsert mode
3. #1693 - Fix resume indexing
4. #1659 - Metadata sidecar files
5. #1432 - Checksum as ID

### Roadmap Long Terme (6-12 mois)
1. #2222/#2221 - Intégration LLM/MCP
2. #1824 - Support OpenSearch
3. #689 - Web Crawler
4. #399 - WatchService
5. #1776 - Elastic Integration Package

---

## 📝 Notes
- Plusieurs issues sont des duplicatas ou liées (#1297/#2255, #1704/#2103)
- Beaucoup de "check_for_bug" nécessitent reproduction avec versions récentes
- Les features LLM/AI sont très demandées mais complexes
- Le support OpenSearch est souvent demandé par la communauté
