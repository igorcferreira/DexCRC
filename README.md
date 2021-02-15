# DexCRC Gradle task

## Challenge

## Implementation

### Non-minified Builds

### R8 minified builds

## Usage

### Single dex file

#### CRC resource string creation

```groovy
android {
    defaultConfig {
        def singleCRCFile = new File("$buildDir/crc/crc.txt")
        resValue "string", "single_crc", singleCRCFile.exists() ? singleCRCFile.readLines().join("\n") : ""
    }
    afterEvaluate {
        // Configuration for Release
        task releaseCRC(dependsOn: minifyReleaseWithR8, type: DexCRCTask) {
            dexPath "$buildDir/intermediates/dex/release/minifyReleaseWithR8/classes.dex"
            outputPath "$buildDir/crc"
            abortIfCRCNotUpdated true
        }
        assembleRelease.dependsOn releaseCRC
        
        //Configuration for Debug
        task debugCRC(dependsOn: mergeExtDexDebug, type: DexCRCTask) {
            dexPath "$buildDir/intermediates/dex/debug/mergeExtDexDebug/classes.dex"
            outputPath "$buildDir/crc"
            abortIfCRCNotUpdated true
        }
        assembleDebug.dependsOn debugCRC

    }
}
```

#### Runtime check

```kotlin
import android.content.Context
import java.util.zip.ZipFile

class CRCValidator {

    data class CRCResponse(val success: Boolean, val calculated: String, val entry: CRCEntry)
    data class CRCEntry(val fileName: String, val crc: String)

    fun validateDexFile(context: Context, entry: CRCEntry): CRCResponse {
        val zipFile = ZipFile(context.packageCodePath)
        val zipEntry = zipFile.getEntry(entry.fileName)
        val crcString = String.format("%02x", zipEntry.crc)
        return CRCResponse(entry.crc == crcString, crcString, entry)
    }
}
```

#### Android APK creation

```shell script
$ ./gradlew clean releaseCRC --disableAbort
$ ./gradlew assembleRelease
```

### Multi-dex files

#### CRC resource string creation

```groovy
android {
    defaultConfig {
        def singleCRCFile = new File("$buildDir/crc/crc_classes.csv")
        resValue "string", "crc_table", singleCRCFile.exists() ? singleCRCFile.readLines().join("\n") : ""
    }
    afterEvaluate {
        // Configuration for Release
        task releaseCRC(dependsOn: minifyReleaseWithR8, type: DexCRCTask) {
            dexPath "$buildDir/intermediates/dex/release/minifyReleaseWithR8"
            outputPath "$buildDir/crc"
            abortIfCRCNotUpdated true
        }
        assembleRelease.dependsOn releaseCRC

        // Configuration for Debug
        task debugCRC(dependsOn: mergeExtDexDebug, type: DexCRCTask) {
            dexPath "$buildDir/intermediates/dex/debug/mergeExtDexDebug"
            outputPath "$buildDir/crc"
            abortIfCRCNotUpdated true
        }
        assembleDebug.dependsOn debugCRC

    }
}
```

#### Runtime check

```kotlin
import android.content.Context
import java.util.zip.ZipFile

class CRCValidator {

    data class CRCResponse(val success: Boolean, val calculated: String, val entry: CRCEntry)
    data class CRCEntry(val fileName: String, val crc: String)
    
    fun validateMultipleDexFile(context: Context, table: String): List<CRCResponse> {
        val entries = expandTable(table)
        return entries.map { validateDexFile(context, it) }
    }

    private fun validateDexFile(context: Context, entry: CRCEntry): CRCResponse {
        val zipFile = ZipFile(context.packageCodePath)
        val zipEntry = zipFile.getEntry(entry.fileName)
        val crcString = String.format("%02x", zipEntry.crc)
        return CRCResponse(entry.crc == crcString, crcString, entry)
    }

    private fun expandTable(table: String): List<CRCEntry> {
        return table.split("\n")
            .mapNotNull {
                val elements = it.split(";")
                if (elements.size == 2) CRCEntry(elements.first(), elements.last()) else null
            }
    }
}
```

#### Android APK creation

```shell script
$ ./gradlew clean releaseCRC --disableAbort
$ ./gradlew assembleRelease
```