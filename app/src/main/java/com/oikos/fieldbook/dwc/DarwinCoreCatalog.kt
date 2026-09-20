package com.oikos.fieldbook.dwc

data class DarwinCoreField(
    val term: String,
    val labelPt: String,
    val labelEn: String,
    val group: String,
    val type: String = "text",
    val enabled: Boolean = true,
    val required: Boolean = false,
    val order: Int,
    /** Vocabulario controlado. Vazio = texto livre. */
    val vocabulary: List<String> = emptyList(),
    /** Unidade declarada para exportacao como MeasurementOrFact. */
    val unit: String = ""
)

/**
 * Vocabularios controlados do Darwin Core.
 * Os valores sao gravados exatamente como o padrao os define (em ingles),
 * porque e isso que o GBIF e o SiBBr consomem; a traducao fica na interface.
 */
object DwcVocabulary {
    val basisOfRecord = listOf(
        "HumanObservation",
        "MachineObservation",
        "PreservedSpecimen",
        "MaterialSample",
        "LivingSpecimen",
        "FossilSpecimen",
        "MaterialCitation"
    )
    val occurrenceStatus = listOf("present", "absent")
    val sex = listOf("male", "female", "hermaphrodite", "undetermined")
    val lifeStage = listOf(
        "egg", "larva", "tadpole", "juvenile", "subadult", "adult",
        "seedling", "sapling", "seed", "unknown"
    )
    val taxonRank = listOf(
        "kingdom", "phylum", "class", "order", "family", "genus",
        "species", "subspecies", "variety", "form"
    )
    val typeStatus = listOf(
        "holotype", "paratype", "isotype", "lectotype", "neotype",
        "syntype", "topotype", "notAType"
    )
    val establishmentMeans = listOf(
        "native", "nativeReintroduced", "introduced",
        "introducedAssistedColonisation", "vagrant", "uncertain"
    )
    val degreeOfEstablishment = listOf(
        "native", "captive", "cultivated", "released", "failing",
        "casual", "reproducing", "established", "colonising",
        "invasive", "widespreadInvasive"
    )
    val pathway = listOf(
        "corridor", "escapeFromConfinement", "releaseInNature",
        "transportContaminant", "transportStowaway", "unaided", "unknown"
    )
    val vitality = listOf("alive", "dead", "mixed", "uncertain")
    val identificationQualifier = listOf("cf.", "aff.", "sp.", "s.l.", "s.str.", "?")
    val identificationVerificationStatus = listOf(
        "unverified", "verifiedBySpecialist", "requiresVerification", "verified"
    )
    val preparations = listOf(
        "exsicata", "alcohol", "driedWhole", "pinned", "tissueInSilica",
        "tissueInEthanol", "skeleton", "skin", "slide", "photographOnly"
    )
    val disposition = listOf(
        "inCollection", "inProcessing", "onLoan", "missing", "destroyed", "notCollected"
    )
    val sampleSizeUnit = listOf(
        "metre", "squareMetre", "hectare", "kilometre", "hour", "minute",
        "day", "trapNight", "individual"
    )
}

object DarwinCoreCatalog {
    val recommended: List<DarwinCoreField> = listOf(
        DarwinCoreField("occurrenceID", "Registro científico (occurrenceID)", "Occurrence ID", "Básicos", enabled = false, order = 0),
        DarwinCoreField("basisOfRecord", "Natureza do registro", "Basis of record", "Básicos", order = 1, vocabulary = DwcVocabulary.basisOfRecord),
        DarwinCoreField("scientificName", "Nome da espécie", "Species name", "Básicos", order = 2),
        DarwinCoreField("vernacularName", "Nome popular", "Common name", "Básicos", order = 2),
        DarwinCoreField("eventDate", "Data da coleta", "Collection date", "Básicos", "date", true, false, 3),
        DarwinCoreField("eventTime", "Hora do registro", "Event time", "Básicos", "time", true, false, 4),
        DarwinCoreField("recordedBy", "Quem coletou", "Recorded by", "Básicos", order = 4),
        DarwinCoreField("decimalLatitude", "Latitude", "Latitude", "Localizacao", "decimal", true, false, 5),
        DarwinCoreField("decimalLongitude", "Longitude", "Longitude", "Localizacao", "decimal", true, false, 6),
        DarwinCoreField("occurrenceRemarks", "Observações da ocorrência", "Occurrence notes", "Básicos", "long_text", true, false, 7),
        DarwinCoreField("coordinateUncertaintyInMeters", "Incerteza da coordenada", "Coordinate uncertainty", "Localizacao", "decimal", true, false, 8),
        DarwinCoreField("geodeticDatum", "Datum geodesico", "Geodetic datum", "Localizacao", order = 9),
        DarwinCoreField("minimumElevationInMeters", "Altitude", "Altitude", "Localizacao", "decimal", false, false, 10),
        DarwinCoreField("locality", "Localidade", "Locality", "Localizacao", order = 11),
        DarwinCoreField("municipality", "Município", "Municipality", "Localização", enabled = false, order = 12),
        DarwinCoreField("stateProvince", "Estado", "State/province", "Localizacao", enabled = false, order = 13),
        DarwinCoreField("country", "Pais", "Country", "Localizacao", enabled = false, order = 14),
        DarwinCoreField("kingdom", "Reino", "Kingdom", "Taxonomia", enabled = false, order = 20),
        DarwinCoreField("phylum", "Filo", "Phylum", "Taxonomia", enabled = false, order = 21),
        DarwinCoreField("class", "Classe", "Class", "Taxonomia", enabled = false, order = 22),
        DarwinCoreField("order", "Ordem", "Order", "Taxonomia", enabled = false, order = 23),
        DarwinCoreField("family", "Família", "Family", "Taxonomia", enabled = false, order = 24),
        DarwinCoreField("genus", "Genero", "Genus", "Taxonomia", enabled = false, order = 25),
        DarwinCoreField("specificEpithet", "Epiteto especifico", "Specific epithet", "Taxonomia", enabled = false, order = 26),
        DarwinCoreField("samplingProtocol", "Método de coleta", "Sampling method", "Amostragem", enabled = false, order = 30),
        DarwinCoreField("samplingEffort", "Esforço amostral", "Sampling effort", "Amostragem", enabled = false, order = 31),
        DarwinCoreField("habitat", "Habitat", "Habitat", "Amostragem", order = 32),
        DarwinCoreField("fieldNumber", "Parcela/transecto/ponto", "Plot/transect/point", "Amostragem", enabled = false, order = 33),
        DarwinCoreField("individualCount", "Número de indivíduos", "Number of individuals", "Ocorrência", "integer", true, false, 40),
        DarwinCoreField("sex", "Sexo", "Sex", "Ocorrência", enabled = false, order = 41, vocabulary = DwcVocabulary.sex),
        DarwinCoreField("lifeStage", "Fase de vida", "Life stage", "Ocorrência", enabled = false, order = 42, vocabulary = DwcVocabulary.lifeStage),
        DarwinCoreField("behavior", "Comportamento", "Behavior", "Ocorrência", enabled = false, order = 43),
        DarwinCoreField("reproductiveCondition", "Condição reprodutiva", "Reproductive condition", "Ocorrência", enabled = false, order = 44),
        DarwinCoreField("occurrenceStatus", "Status da ocorrência", "Occurrence status", "Ocorrência", order = 45, vocabulary = DwcVocabulary.occurrenceStatus),
        DarwinCoreField("identifiedBy", "Quem identificou", "Identified by", "Identificacao", enabled = false, order = 50),
        DarwinCoreField("dateIdentified", "Data da identificacao", "Identification date", "Identificacao", "date", false, false, 51),
        DarwinCoreField("identificationQualifier", "Qualificador da identificação", "Identification qualifier", "Identificação", enabled = false, order = 52, vocabulary = DwcVocabulary.identificationQualifier),
        DarwinCoreField("identificationRemarks", "Observações da identificação", "Identification notes", "Identificação", "long_text", false, false, 53),
        DarwinCoreField("associatedMedia", "Foto/áudio", "Photo/audio", "Mídia", enabled = false, order = 60),
        DarwinCoreField("associatedReferences", "Link externo", "External link", "Mídia", enabled = false, order = 61),
        DarwinCoreField("recordNumber", "Número de campo", "Record number", "Básicos", enabled = false, order = 70),
        DarwinCoreField("organismQuantity", "Quantidade do organismo", "Organism quantity", "Ocorrência", enabled = false, order = 71),
        DarwinCoreField("organismQuantityType", "Tipo de quantidade", "Organism quantity type", "Ocorrência", enabled = false, order = 72),
        DarwinCoreField("substrate", "Substrato", "Substrate", "Ambiente", enabled = false, order = 73),
        DarwinCoreField("fieldNotes", "Anotações de campo", "Field notes", "Anotações", "long_text", enabled = false, order = 74),
        DarwinCoreField("measurementType", "Tipo de medida", "Measurement type", "MeasurementOrFact", enabled = false, order = 80),
        DarwinCoreField("measurementValue", "Valor da medida", "Measurement value", "MeasurementOrFact", enabled = false, order = 81),
        DarwinCoreField("measurementUnit", "Unidade da medida", "Measurement unit", "MeasurementOrFact", enabled = false, order = 82)
    )

    val advancedOccurrenceFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("animalGroup", "Grupo animal", "Animal group", "Fauna", enabled = true, order = 100),
        DarwinCoreField("recordType", "Tipo de registro", "Record type", "Fauna", enabled = true, order = 101),
        DarwinCoreField("activity", "Atividade", "Activity", "Fauna", enabled = true, order = 102),
        DarwinCoreField("microhabitat", "Microhabitat", "Microhabitat", "Fauna", enabled = true, order = 103),
        DarwinCoreField("heightAboveGround", "Altura em relação ao solo", "Height above ground", "Fauna", "decimal", enabled = false, order = 104),
        DarwinCoreField("distanceToWater", "Distância da água", "Distance to water", "Fauna", "decimal", enabled = false, order = 105),
        DarwinCoreField("airTemperature", "Temperatura do ar", "Air temperature", "Fauna", "decimal", enabled = false, order = 106),
        DarwinCoreField("humidity", "Umidade", "Humidity", "Fauna", "decimal", enabled = false, order = 107),
        DarwinCoreField("vocalizationPresent", "Vocalização presente", "Vocalization present", "Fauna", enabled = false, order = 108),
        DarwinCoreField("vocalizationType", "Tipo de vocalização", "Vocalization type", "Fauna", enabled = false, order = 109),
        DarwinCoreField("vocalizationContext", "Contexto da vocalização", "Vocalization context", "Fauna", enabled = false, order = 110),
        DarwinCoreField("callingIndividuals", "Indivíduos vocalizando", "Calling individuals", "Fauna", "integer", enabled = false, order = 111),
        DarwinCoreField("audioQuality", "Qualidade do áudio", "Audio quality", "Fauna", enabled = false, order = 112),
        DarwinCoreField("backgroundNoise", "Ruído de fundo", "Background noise", "Fauna", enabled = false, order = 113),
        DarwinCoreField("behaviorNotes", "Observações comportamentais", "Behavior notes", "Fauna", "long_text", enabled = false, order = 114),

        DarwinCoreField("plantHabit", "Hábito", "Plant habit", "Botanica", enabled = true, order = 130),
        DarwinCoreField("estimatedHeight", "Altura estimada", "Estimated height", "Botanica", "decimal", enabled = true, order = 131),
        DarwinCoreField("dbh", "DAP", "DBH", "Botanica", "decimal", enabled = true, order = 132),
        DarwinCoreField("cap", "CAP", "CBH", "Botanica", "decimal", enabled = true, order = 133),
        DarwinCoreField("flowerPresent", "Presença de flor", "Flower present", "Botanica", enabled = true, order = 134),
        DarwinCoreField("fruitPresent", "Presença de fruto", "Fruit present", "Botanica", enabled = true, order = 135),
        DarwinCoreField("phenologicalState", "Estado fenológico", "Phenological state", "Botanica", enabled = true, order = 136),
        DarwinCoreField("stemType", "Tipo de caule", "Stem type", "Botanica", enabled = false, order = 137),
        DarwinCoreField("bark", "Casca", "Bark", "Botanica", enabled = false, order = 138),
        DarwinCoreField("latexPresent", "Presença de látex", "Latex present", "Botanica", enabled = false, order = 139),
        DarwinCoreField("leafType", "Folha simples/composta", "Leaf type", "Botanica", enabled = false, order = 140),
        DarwinCoreField("phyllotaxy", "Filotaxia", "Phyllotaxy", "Botanica", enabled = false, order = 141),
        DarwinCoreField("leafMargin", "Margem da folha", "Leaf margin", "Botanica", enabled = false, order = 142),
        DarwinCoreField("leafApex", "Ápice foliar", "Leaf apex", "Botanica", enabled = false, order = 143),
        DarwinCoreField("leafBase", "Base foliar", "Leaf base", "Botanica", enabled = false, order = 144),
        DarwinCoreField("venation", "Nervação", "Venation", "Botanica", enabled = false, order = 145),
        DarwinCoreField("flowerColor", "Cor da flor", "Flower color", "Botanica", enabled = false, order = 146),
        DarwinCoreField("fruitType", "Tipo de fruto", "Fruit type", "Botanica", enabled = false, order = 147),
        DarwinCoreField("plantPhotoParts", "Partes fotografadas", "Photographed plant parts", "Botanica", enabled = false, order = 148),
        DarwinCoreField("botanicalNotes", "Observações botânicas", "Botanical notes", "Botanica", "long_text", enabled = false, order = 149),

        DarwinCoreField("traceType", "Tipo de vestígio", "Trace type", "Vestígios", enabled = false, order = 170),
        DarwinCoreField("probableSpecies", "Espécie provável", "Probable species", "Vestígios", enabled = false, order = 171),
        DarwinCoreField("identificationConfidence", "Confiança da identificação", "Identification confidence", "Vestígios", enabled = false, order = 172),
        DarwinCoreField("traceConservation", "Estado de conservação do vestígio", "Trace conservation", "Vestígios", enabled = false, order = 173),
        DarwinCoreField("traceMeasurements", "Medidas do vestígio", "Trace measurements", "Vestígios", enabled = false, order = 174),
        DarwinCoreField("movementDirection", "Direção do deslocamento", "Movement direction", "Vestígios", enabled = false, order = 175),
        DarwinCoreField("traceNotes", "Observações do vestígio", "Trace notes", "Vestígios", "long_text", enabled = false, order = 176),

        DarwinCoreField("fungusSubstrate", "Substrato", "Substrate", "Fungos", enabled = true, order = 190),
        DarwinCoreField("fruitingBodyPresent", "Corpo de frutificação presente", "Fruiting body present", "Fungos", enabled = true, order = 191),
        DarwinCoreField("fruitingBodyColor", "Cor do corpo de frutificação", "Fruiting body color", "Fungos", enabled = true, order = 192),
        DarwinCoreField("fruitingBodyShape", "Forma do corpo de frutificação", "Fruiting body shape", "Fungos", enabled = false, order = 193),
        DarwinCoreField("capDiameter", "Diâmetro do píleo/chapéu", "Cap diameter", "Fungos", "decimal", enabled = false, order = 194),
        DarwinCoreField("stipeLength", "Comprimento do estipe", "Stipe length", "Fungos", "decimal", enabled = false, order = 195),
        DarwinCoreField("gillOrPoreType", "Lamelas, poros ou superfície fértil", "Gill, pore or fertile surface", "Fungos", enabled = false, order = 196),
        DarwinCoreField("sporePrintColor", "Cor da esporada", "Spore print color", "Fungos", enabled = false, order = 197),
        DarwinCoreField("hostOrAssociation", "Hospedeiro ou associação", "Host or association", "Fungos", enabled = false, order = 198),
        DarwinCoreField("decayStage", "Estado de decomposição do substrato", "Substrate decay stage", "Fungos", enabled = false, order = 199),
        DarwinCoreField("fungusMicrohabitat", "Microhabitat", "Microhabitat", "Fungos", enabled = false, order = 200),
        DarwinCoreField("fungiNotes", "Observações micológicas", "Mycological notes", "Fungos", "long_text", enabled = false, order = 201),

        DarwinCoreField("quickNotes", "Anotacoes rapidas", "Quick notes", "Anotacoes", "long_text", enabled = false, order = 220),
        DarwinCoreField("detailedDescription", "Descrição detalhada", "Detailed description", "Anotacoes", "long_text", enabled = false, order = 221),
        DarwinCoreField("environmentalConditions", "Condicoes ambientais", "Environmental conditions", "Ambiente", "long_text", enabled = false, order = 222),
        DarwinCoreField("ecologicalNotes", "Observações ecológicas", "Ecological notes", "Ambiente", "long_text", enabled = false, order = 223),
        DarwinCoreField("identificationQuestions", "Duvidas de identificacao", "Identification questions", "Identificacao", "long_text", enabled = false, order = 224),
        DarwinCoreField("identificationHypothesis", "Hipotese de identificacao", "Identification hypothesis", "Identificacao", enabled = false, order = 225),
        DarwinCoreField("labNotes", "Observações para laboratório", "Lab notes", "Anotacoes", "long_text", enabled = false, order = 226),
        DarwinCoreField("collectionNotes", "Observações para herbário/coleção", "Collection notes", "Anotacoes", "long_text", enabled = false, order = 227)
    )


    /** Coleção e tombo — habilita uso em herbário e coleção zoológica. */
    val collectionFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("catalogNumber", "Número de tombo", "Catalog number", "Coleção", enabled = false, order = 240),
        DarwinCoreField("otherCatalogNumbers", "Outros números de tombo", "Other catalog numbers", "Coleção", enabled = false, order = 241),
        DarwinCoreField("institutionCode", "Sigla da instituição", "Institution code", "Coleção", enabled = false, order = 242),
        DarwinCoreField("collectionCode", "Sigla da coleção", "Collection code", "Coleção", enabled = false, order = 243),
        DarwinCoreField("preparations", "Tipo de preparação", "Preparations", "Coleção", enabled = false, order = 244, vocabulary = DwcVocabulary.preparations),
        DarwinCoreField("disposition", "Destino do material", "Disposition", "Coleção", enabled = false, order = 245, vocabulary = DwcVocabulary.disposition),
        DarwinCoreField("typeStatus", "Status de tipo nomenclatural", "Type status", "Coleção", enabled = false, order = 246, vocabulary = DwcVocabulary.typeStatus),
        DarwinCoreField("associatedSequences", "Sequências associadas", "Associated sequences", "Coleção", enabled = false, order = 247),
        DarwinCoreField("organismID", "Identificador do indivíduo", "Organism ID", "Coleção", enabled = false, order = 248)
    )

    /** Taxonomia e identificação — resolução nomenclatural completa. */
    val taxonomyFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("scientificNameAuthorship", "Autoria do nome científico", "Scientific name authorship", "Taxonomia", enabled = false, order = 27),
        DarwinCoreField("infraspecificEpithet", "Epíteto infraespecífico", "Infraspecific epithet", "Taxonomia", enabled = false, order = 28),
        DarwinCoreField("taxonRank", "Nível taxonômico", "Taxon rank", "Taxonomia", enabled = false, order = 29, vocabulary = DwcVocabulary.taxonRank),
        DarwinCoreField("higherClassification", "Classificação superior", "Higher classification", "Taxonomia", enabled = false, order = 30),
        DarwinCoreField("taxonRemarks", "Observações taxonômicas", "Taxon remarks", "Taxonomia", "long_text", enabled = false, order = 31),
        DarwinCoreField("verbatimIdentification", "Identificação como anotada", "Verbatim identification", "Identificação", enabled = false, order = 54),
        DarwinCoreField("identificationVerificationStatus", "Status de verificação", "Identification verification status", "Identificação", enabled = false, order = 55, vocabulary = DwcVocabulary.identificationVerificationStatus)
    )

    /** Ecologia e conservação. */
    val ecologyFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("associatedTaxa", "Táxons associados", "Associated taxa", "Ecologia", enabled = false, order = 260, vocabulary = emptyList()),
        DarwinCoreField("establishmentMeans", "Origem", "Establishment means", "Ecologia", enabled = false, order = 261, vocabulary = DwcVocabulary.establishmentMeans),
        DarwinCoreField("degreeOfEstablishment", "Grau de estabelecimento", "Degree of establishment", "Ecologia", enabled = false, order = 262, vocabulary = DwcVocabulary.degreeOfEstablishment),
        DarwinCoreField("pathway", "Via de introdução", "Pathway", "Ecologia", enabled = false, order = 263, vocabulary = DwcVocabulary.pathway),
        DarwinCoreField("vitality", "Condição vital", "Vitality", "Ecologia", enabled = false, order = 264, vocabulary = DwcVocabulary.vitality),
        DarwinCoreField("associatedOccurrences", "Ocorrências associadas", "Associated occurrences", "Ecologia", enabled = false, order = 265)
    )

    /** Georreferenciamento rastreável. */
    val georeferenceFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("verbatimCoordinates", "Coordenada como lida", "Verbatim coordinates", "Georreferenciamento", enabled = false, order = 280),
        DarwinCoreField("verbatimSRS", "Sistema de referência original", "Verbatim SRS", "Georreferenciamento", enabled = false, order = 281),
        DarwinCoreField("coordinatePrecision", "Precisão da coordenada", "Coordinate precision", "Georreferenciamento", "decimal", enabled = false, order = 282),
        DarwinCoreField("maximumElevationInMeters", "Altitude máxima", "Maximum elevation", "Georreferenciamento", "decimal", enabled = false, order = 283, unit = "m"),
        DarwinCoreField("georeferenceProtocol", "Protocolo de georreferenciamento", "Georeference protocol", "Georreferenciamento", enabled = false, order = 284),
        DarwinCoreField("georeferenceSources", "Fontes do georreferenciamento", "Georeference sources", "Georreferenciamento", enabled = false, order = 285),
        DarwinCoreField("georeferencedBy", "Quem georreferenciou", "Georeferenced by", "Georreferenciamento", enabled = false, order = 286),
        DarwinCoreField("georeferenceRemarks", "Observações do georreferenciamento", "Georeference remarks", "Georreferenciamento", "long_text", enabled = false, order = 287),
        DarwinCoreField("countryCode", "Código do país", "Country code", "Georreferenciamento", enabled = false, order = 288),
        DarwinCoreField("locationRemarks", "Observações da localidade", "Location remarks", "Georreferenciamento", "long_text", enabled = false, order = 289),
        DarwinCoreField("waterBody", "Corpo d'água", "Water body", "Georreferenciamento", enabled = false, order = 290),
        DarwinCoreField("island", "Ilha", "Island", "Georreferenciamento", enabled = false, order = 291)
    )

    /** Autoria e crédito. */
    val attributionFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("recordedByID", "ORCID de quem coletou", "Recorded by ID", "Autoria", enabled = false, order = 300),
        DarwinCoreField("identifiedByID", "ORCID de quem identificou", "Identified by ID", "Autoria", enabled = false, order = 301),
        DarwinCoreField("datasetName", "Nome do conjunto de dados", "Dataset name", "Autoria", enabled = false, order = 302),
        DarwinCoreField("rightsHolder", "Detentor dos direitos", "Rights holder", "Autoria", enabled = false, order = 303),
        DarwinCoreField("bibliographicCitation", "Citação bibliográfica", "Bibliographic citation", "Autoria", enabled = false, order = 304)
    )

    /** Medidas — complementos do MeasurementOrFact. */
    val measurementFields: List<DarwinCoreField> = listOf(
        DarwinCoreField("measurementMethod", "Método da medida", "Measurement method", "MeasurementOrFact", enabled = false, order = 83),
        DarwinCoreField("measurementDeterminedBy", "Quem mediu", "Measurement determined by", "MeasurementOrFact", enabled = false, order = 84),
        DarwinCoreField("measurementDeterminedDate", "Data da medida", "Measurement date", "MeasurementOrFact", "date", enabled = false, order = 85),
        DarwinCoreField("measurementAccuracy", "Acurácia da medida", "Measurement accuracy", "MeasurementOrFact", "decimal", enabled = false, order = 86)
    )

    val allFields: List<DarwinCoreField> =
        recommended + advancedOccurrenceFields + collectionFields + taxonomyFields +
            ecologyFields + georeferenceFields + attributionFields + measurementFields

    /**
     * Termos proprios do Oikos, sem equivalente no Darwin Core (morfologia,
     * bioacustica, vestigios, micologia). No Darwin Core Archive eles nao podem
     * virar coluna do core, porque nao existe URI da TDWG para eles: saem como
     * linhas da extensao MeasurementOrFact.
     */
    val customTerms: Set<String> = advancedOccurrenceFields.map { it.term }.toSet()

    /** Indice termo -> definicao, para consulta rapida no formulario e no exportador. */
    val byTerm: Map<String, DarwinCoreField> = allFields.associateBy { it.term }

    val coreOccurrenceTerms = listOf(
        "occurrenceID",
        "eventID",
        "basisOfRecord",
        "occurrenceStatus",
        "scientificName",
        "vernacularName",
        "taxonRank",
        "kingdom",
        "phylum",
        "class",
        "order",
        "family",
        "genus",
        "specificEpithet",
        "individualCount",
        "sex",
        "lifeStage",
        "behavior",
        "reproductiveCondition",
        "organismQuantity",
        "organismQuantityType",
        "recordedBy",
        "identifiedBy",
        "dateIdentified",
        "identificationQualifier",
        "identificationRemarks",
        "eventDate",
        "eventTime",
        "samplingProtocol",
        "samplingEffort",
        "fieldNumber",
        "habitat",
        "locality",
        "municipality",
        "stateProvince",
        "country",
        "decimalLatitude",
        "decimalLongitude",
        "coordinateUncertaintyInMeters",
        "geodeticDatum",
        "minimumElevationInMeters",
        "occurrenceRemarks",
        "associatedMedia",
        "associatedReferences",
        "associatedSequences",
        "preparations",
        "disposition"
    )
}
