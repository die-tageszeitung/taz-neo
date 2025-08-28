package de.taz.app.android.api

import de.taz.app.android.util.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

@Serializable(with = TestTypeDtoEnumSerializer::class)
private enum class TestType {
    foo,
    @JsonNames("bar")
    world,
    UNKNOWN
}

private object TestTypeDtoEnumSerializer :
    EnumSerializer<TestType>(TestType.values(), TestType.UNKNOWN)

@Serializable
private data class TestTypeWrapper(
    val testType: TestType,
    val other: String
)

class EnumSerializerTest {

    // Use the global jsonDecoder for more realistic test coverage
    private val jsonDecoder = Json

    @Test
    fun `Known value foo is mapped correctly`() {
        val json = """
                "foo"
            """.trimIndent()

        val result = jsonDecoder.decodeFromString<TestType>(json)

        assertEquals(TestType.foo, result)
    }

    @Test
    fun `Unknown value is mapped to UNKNOWN`() {
        val json = """
                "something"
            """.trimIndent()

        val result = jsonDecoder.decodeFromString<TestType>(json)

        assertEquals(TestType.UNKNOWN, result)
    }

    @Test
    fun `Wrapped known value is mapped correctly`() {
        val json = """
            {
                "testType": "foo",
                "other": "other"
            }
            """.trimIndent()

        val result = jsonDecoder.decodeFromString<TestTypeWrapper>(json)

        assertEquals(TestTypeWrapper(TestType.foo, "other"), result)
    }

    @Test
    fun `Wrapped Unknown value is mapped to UNKNOWN`() {
        val json = """
            {
                "testType": "something",
                "other": "other"
            }
            """.trimIndent()

        val result = jsonDecoder.decodeFromString<TestTypeWrapper>(json)

        assertEquals(TestTypeWrapper(TestType.UNKNOWN, "other"), result)
    }

    @Ignore("@JsonNames annotation are not supported yet")
    @Test
    fun `Known value bar is mapped to specified enum value world`() {
        val json = """
                "bar"
            """.trimIndent()

        val result = jsonDecoder.decodeFromString<TestType>(json)

        assertEquals(TestType.world, result)
    }
}