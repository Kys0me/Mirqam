package rtlide.lang.loader

import kotlinx.serialization.json.Json
import rtlide.lang.schema.LanguageDefinition
import java.io.File

/**
 * Loads language definitions from JSON. (YAML support can be added with
 * com.charleskorn.kaml:kaml — the schema classes are already @Serializable.)
 * Uses the generated serializer explicitly so no reified helpers are required.
 */
object GrammarLoader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    fun parse(text: String): LanguageDefinition =
        json.decodeFromString(LanguageDefinition.serializer(), text)

    fun loadJson(file: File): LanguageDefinition = parse(file.readText(Charsets.UTF_8))

    fun encode(def: LanguageDefinition): String =
        json.encodeToString(LanguageDefinition.serializer(), def)
}
