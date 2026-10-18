# Oikos FieldBook

Aplicativo Android nativo em Kotlin + Jetpack Compose para coleta ecologica e biologica offline, com banco local Room/SQLite e exportacoes cientificas baseadas em Darwin Core.

DOI (todas as versões): [10.5281/zenodo.20526746](https://doi.org/10.5281/zenodo.20526746)

Como citar (APA): Maciel, L. S. C. (2026). *Oikos FieldBook* (Version 0.2.0) [Computer software]. Zenodo. https://doi.org/10.5281/zenodo.20526746

## Implementado neste MVP

- Projetos de pesquisa offline.
- Configuracao de campos Darwin Core por projeto.
- Expedicoes de coleta mapeadas para `Event`.
- Criação de expedição com data/hora automáticas, GPS inicial quando permitido e campos editáveis de localidade, equipe, ambiente e esforço.
- Estrutura de trilha GPS de expedição com pontos exportáveis como CSV e GeoJSON LineString.
- Ocorrências biológicas mapeadas para `Occurrence`, `Taxon`, `Identification`, `Location` e campos dinâmicos.
- Mídias locais de foto e áudio como entidades separadas, sem salvar binários no SQLite.
- Áudio gravado só em WAV (PCM 16 bits, 44,1 kHz, mono), sem compressão; arquivos MP3, M4A e outros podem ser anexados.
- Captura de GPS com latitude, longitude, altitude, precisao, datum e edicao manual.
- Datum padrão `SIRGAS 2000`, com suporte também a `WGS84`.
- Coordenadas armazenadas internamente em graus decimais.
- Exportacao para CSV Darwin Core, JSON completo, GeoJSON para QGIS e XLSX OpenXML com abas.
- Exportação ZIP organizada com `README.txt`, `manifest.json`, pasta `dados/` e pasta `midias/`.
- `associatedMedia` usa caminhos relativos separados por ponto e vírgula, preservando o vínculo entre ocorrências, expedições, fotos e áudios.
- Compartilhamento via Android Share Sheet, incluindo Google Drive quando disponivel.
- Tela de uso de armazenamento e limpeza de cache.
- Identidade visual própria com tema noturno padrão grafite/laranja, tema claro alternável, tipografia e shapes Material 3.
- Nova logo oficial aplicada ao launcher, splash e telas principais.
- Splash screen escura com logo grande, sem flash branco.
- Adaptive icon Android com fundo escuro e simbolo ecologico laranja.
- HUD modernizada com hero visual, acoes rapidas de campo, cards com badges e modo simples/coleta rapida.
- Assinatura discreta: `Developed by: MACIEL, L. S. C.`
- Ajuda contextual para termos técnicos e fluxo guiado para usuários que não conhecem Darwin Core.
- Ocorrência com tipos de formulário, seções avançadas opcionais para fauna, flora, funga, vestígios, ambiente/anotações e campos dinâmicos exportáveis como MeasurementOrFact.
- Mídias dentro da ocorrência com descrição e assunto do registro.

## Estrutura

- `app/src/main/java/com/oikos/fieldbook/data`: entidades Room, DAOs, banco e repositorio.
- `app/src/main/java/com/oikos/fieldbook/dwc`: catalogo Darwin Core PT/EN.
- `app/src/main/java/com/oikos/fieldbook/export`: CSV, JSON, GeoJSON e XLSX.
- `app/src/main/java/com/oikos/fieldbook/services`: GPS e arquivos locais de mídia/exportação.
- `app/src/main/java/com/oikos/fieldbook/MainActivity.kt`: interface Compose e fluxo principal.

## Build

Abra esta pasta no Android Studio recente e sincronize o Gradle. O projeto usa:

- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Compose Material 3
- Room 2.6.1
- CameraX
- Fused Location Provider

Este ambiente local não possui JDK/Gradle instalados, então o APK não foi compilado aqui.

