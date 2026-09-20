package com.oikos.fieldbook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [Index("name")]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val institution: String = "",
    val principalInvestigator: String = "",
    val language: String = "pt",
    val defaultDatum: String = "SIRGAS 2000",
    val coordinateFormat: String = "DD",
    val photoQuality: String = "medium",
    val audioFormat: String = "wav",
    val enableFauna: Boolean = true,
    val enableBotany: Boolean = false,
    val enableFungi: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "field_configs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index(value = ["projectId", "dwcTerm"], unique = true)]
)
data class FieldConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val dwcTerm: String,
    val labelPt: String,
    val labelEn: String,
    val enabled: Boolean = true,
    val required: Boolean = false,
    val fieldType: String = "text",
    val groupName: String = "Basics",
    val displayOrder: Int = 0
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index(value = ["eventId"], unique = true)]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val eventId: String,
    val name: String,
    val eventDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val locality: String = "",
    val municipality: String = "",
    val stateProvince: String = "",
    val country: String = "",
    val habitat: String = "",
    val environmentType: String = "",
    val samplingProtocol: String = "",
    val samplingEffort: String = "",
    val recordedBy: String = "",
    val fieldTeam: String = "",
    val gpsTrackEnabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val coordinateUncertaintyInMeters: Double? = null,
    val geodeticDatum: String = "SIRGAS 2000",
    val coordinateCapturedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("sessionId"),
        Index(value = ["occurrenceId"], unique = true)
    ]
)
data class OccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sessionId: Long,
    val occurrenceId: String,
    val scientificName: String = "",
    val vernacularName: String = "",
    val basisOfRecord: String = "HumanObservation",
    val occurrenceType: String = "",
    val formType: String = "Geral",
    val recordNumber: String = "",
    val occurrenceStatus: String = "present",
    val individualCount: Int? = null,
    val sex: String = "",
    val lifeStage: String = "",
    val behavior: String = "",
    val reproductiveCondition: String = "",
    val recordedBy: String = "",
    val identifiedBy: String = "",
    val dateIdentified: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val coordinateUncertaintyInMeters: Double? = null,
    val geodeticDatum: String = "SIRGAS 2000",
    val occurrenceRemarks: String = "",
    val fieldNotes: String = "",
    val dynamicFieldsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("sessionId"), Index("occurrenceId"), Index("mediaType")]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sessionId: Long,
    val occurrenceId: Long? = null,
    val mediaType: String,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordinateUncertaintyInMeters: Double? = null,
    val caption: String = "",
    val mediaSubject: String = "",
    val bodyPartOrPlantPart: String = "",
    val behaviorContext: String = "",
    val notes: String = ""
)

@Entity(
    tableName = "gps_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("sessionId"), Index("eventId")]
)
data class GpsTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sessionId: Long,
    val eventId: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistance: Double = 0.0,
    val duration: Long = 0,
    val pointsCount: Int = 0,
    val coordinateFormat: String = "DD",
    val geodeticDatum: String = "SIRGAS 2000",
    val active: Boolean = false
)

@Entity(
    tableName = "gps_track_points",
    foreignKeys = [
        ForeignKey(
            entity = GpsTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("timestamp")]
)
data class GpsTrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Double? = null
)
