package com.securechat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, ContactEntity::class, MessageEntity::class, StatusEntity::class, PollEntity::class, GroupEntity::class, GroupSenderKeyEntity::class, MusicMetadataEntity::class],
    version = 40,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun statusDao(): StatusDao
    abstract fun pollDao(): PollDao
    abstract fun groupDao(): GroupDao
    abstract fun groupSenderKeyDao(): GroupSenderKeyDao
    abstract fun musicMetadataDao(): MusicMetadataDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_me ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_me ADD COLUMN isAdmin INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS statuses (
                        statusId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        userImage TEXT,
                        mediaUrl TEXT NOT NULL,
                        mediaType TEXT NOT NULL,
                        durationHours INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS polls (
                        pollId TEXT NOT NULL PRIMARY KEY,
                        question TEXT NOT NULL,
                        optionsJson TEXT NOT NULL,
                        resultsJson TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        receiverId TEXT,
                        userVote INTEGER NOT NULL DEFAULT -1,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        groupId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        memberCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToContent TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToSenderId TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN clientMessageId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN deliveryStatus INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isImportant INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_me ADD COLUMN info TEXT")
                db.execSQL("ALTER TABLE user_me ADD COLUMN links TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToMediaType TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_me ADD COLUMN isCreator INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_me ADD COLUMN styx INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_me ADD COLUMN ageVerified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_me ADD COLUMN statusPermitted TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isAudioPlayed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `groups` ADD COLUMN groupImageUrl TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isDeliveredAsNotification INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Emoji-Reaktion auf eine Nachricht (null = keine Reaktion)
                db.execSQL("ALTER TABLE messages ADD COLUMN reaction TEXT")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // E2EE-Flag: False = Altlast/Klartext, True = AES-256-GCM verschlüsselt
                db.execSQL("ALTER TABLE messages ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
                // Telefon-Verifikation: False = noch nicht per OTP bestätigt
                db.execSQL("ALTER TABLE user_me ADD COLUMN isPhoneVerified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Multi-Device Fan-Out: UUID des sendenden Linked-Device (null = primäres Android)
                db.execSQL("ALTER TABLE messages ADD COLUMN senderDeviceId TEXT")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Gruppen-E2EE: Sender-Key-Cache (Sender-Key-Verfahren, AES-256-GCM)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_sender_keys (
                        groupId TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        keyBase64 TEXT NOT NULL,
                        version INTEGER NOT NULL DEFAULT 1,
                        storedAt INTEGER NOT NULL,
                        PRIMARY KEY (groupId, ownerId)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Bot-Flag für Kontakte (True = Bot-Konto)
                db.execSQL("ALTER TABLE contacts ADD COLUMN isBot INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Lokaler Spitzname (überschreibt username in der Anzeige, rein clientseitig)
                db.execSQL("ALTER TABLE contacts ADD COLUMN customAlias TEXT")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // is18Verified: 18+ verifiziert (direkt oder per Zeitablauf nach 16er-Verif.)
                db.execSQL("ALTER TABLE user_me ADD COLUMN is18Verified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // LetheID: eindeutige menschenlesbare ID zum Kontakt-Hinzufügen
                db.execSQL("ALTER TABLE user_me ADD COLUMN letheId TEXT")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Anonymer LetheID-Kontakt: kein Name/Bild/Nummer sichtbar, Spieleinschränkungen
                db.execSQL("ALTER TABLE contacts ADD COLUMN isAnonymous INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Soziale-Medien-Felder für Nutzer und Kontakte
                db.execSQL("ALTER TABLE user_me ADD COLUMN instagram TEXT")
                db.execSQL("ALTER TABLE user_me ADD COLUMN tiktok TEXT")
                db.execSQL("ALTER TABLE user_me ADD COLUMN youtube TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN instagram TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN tiktok TEXT")
                db.execSQL("ALTER TABLE contacts ADD COLUMN youtube TEXT")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // "Über mich"-Text für Kontakte
                db.execSQL("ALTER TABLE contacts ADD COLUMN info TEXT")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Globaler Moderator-Status
                db.execSQL("ALTER TABLE user_me ADD COLUMN isModerator INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Gruppen-Beschreibung
                db.execSQL("ALTER TABLE groups ADD COLUMN description TEXT")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Mehrfachauswahl für Umfragen
                db.execSQL("ALTER TABLE polls ADD COLUMN allowMultipleChoice INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE polls ADD COLUMN userVotesJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Metadaten-Cache für MP3-Player (ID3-Tags + Cover-Pfad)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS music_metadata (
                        url TEXT NOT NULL PRIMARY KEY,
                        title TEXT,
                        artist TEXT,
                        coverPath TEXT,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Server-ID der zitierten Nachricht (für Scroll-to-Quote)
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToMessageId TEXT")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // LetheID des Kontakts – für automatischen anonymen Chat-Flow beim Kontakt-Teilen
                db.execSQL("ALTER TABLE contacts ADD COLUMN letheId TEXT")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Bezahlter Verifizierungshaken des Kontakts / eigenes Profil
                db.execSQL("ALTER TABLE contacts ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_me ADD COLUMN isVerified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Musik-Metadaten für Bild-Statuses
                db.execSQL("ALTER TABLE statuses ADD COLUMN musicUrl TEXT")
                db.execSQL("ALTER TABLE statuses ADD COLUMN musicTitle TEXT")
                db.execSQL("ALTER TABLE statuses ADD COLUMN musicArtist TEXT")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Musik-Dauer und Start-Offset für Bild-Statuses
                db.execSQL("ALTER TABLE statuses ADD COLUMN musicDurationSec INTEGER")
                db.execSQL("ALTER TABLE statuses ADD COLUMN musicOffsetSec REAL")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // P2P-Flag: Nachricht wurde direkt per WebRTC DataChannel übertragen (kein Server-Roundtrip)
                db.execSQL("ALTER TABLE messages ADD COLUMN isP2pDelivered INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Externer Link (z.B. APK-Download) auf Bild-Statuses, beim Tippen extern geöffnet
                db.execSQL("ALTER TABLE statuses ADD COLUMN linkUrl TEXT")
                db.execSQL("ALTER TABLE statuses ADD COLUMN linkLabel TEXT")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Opt-in Chat-Backup: Dedup-Flag, verhindert erneuten Upload bereits gesicherter Nachrichten
                db.execSQL("ALTER TABLE messages ADD COLUMN backedUp INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // True = der Partner hat MICH blockiert (ich kann ihm nicht mehr schreiben)
                db.execSQL("ALTER TABLE contacts ADD COLUMN blockedByPartner INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Persistierter Bild-Cache-Abgleich: letzte Prüfung + Server-Timestamp des Bildes
                db.execSQL("ALTER TABLE contacts ADD COLUMN imageCheckedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contacts ADD COLUMN imageUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN imageCheckedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN imageUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
