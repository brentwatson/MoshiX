/*
 * Copyright (C) 2021 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.moshix.sealed.codegen.ksp

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile.Companion.kotlin
import com.tschuchort.compiletesting.kspArgs
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import dev.zacsweers.moshix.sealed.codegen.ksp.MoshiSealedSymbolProcessorProvider.Companion.OPTION_GENERATE_PROGUARD_RULES
import java.io.File
import org.junit.Test

class MoshiSealedSymbolProcessorProviderTest {

  @Test
  fun smokeTest() {
    val source =
      kotlin(
        "CustomCallable.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b")
        class TypeB : BaseType()
      }
    """
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(source)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedAdapter = File(generatedSourcesDir, "kotlin/test/BaseTypeJsonAdapter.kt")
    assertThat(generatedAdapter.exists()).isTrue()
    // language=kotlin
    assertThat(generatedAdapter.readText().trim())
      .isEqualTo(
        """
      // Code generated by moshi-sealed. Do not edit.
      package test

      import com.squareup.moshi.JsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.Moshi
      import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
      import kotlin.Suppress
      import kotlin.Unit
      import kotlin.collections.emptySet

      @Suppress("DEPRECATION", "unused", "ClassName", "REDUNDANT_PROJECTION", "RedundantExplicitType",
          "LocalVariableName", "RedundantVisibilityModifier")
      public class BaseTypeJsonAdapter(
        moshi: Moshi,
      ) : JsonAdapter<BaseType>() {
        @Suppress("UNCHECKED_CAST")
        private val runtimeAdapter: JsonAdapter<BaseType> =
            PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
              .withSubtype(BaseType.TypeA::class.java, "a")
              .withSubtype(BaseType.TypeA::class.java, "aa")
              .withSubtype(BaseType.TypeB::class.java, "b")
              .create(BaseType::class.java, emptySet(), moshi) as JsonAdapter<BaseType>


        public override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)

        public override fun toJson(writer: JsonWriter, value_: BaseType?): Unit {
          runtimeAdapter.toJson(writer, value_)
        }
      }
      """.trimIndent()
      )

    val proguardFiles = generatedSourcesDir.walkTopDown().filter { it.extension == "pro" }.toList()
    check(proguardFiles.isNotEmpty())
    proguardFiles.forEach { generatedFile ->
      when (generatedFile.nameWithoutExtension) {
        "moshi-sealed-test.BaseType" ->
          assertThat(generatedFile.readText())
            .contains(
              """
          -if class test.BaseType
          -keepnames class test.BaseType
          -if class test.BaseType
          -keep class test.BaseTypeJsonAdapter {
              public <init>(com.squareup.moshi.Moshi);
          }
          """.trimIndent()
            )
        else -> error("Unrecognized proguard file: $generatedFile")
      }
    }
  }

  @Test
  fun disableProguardGeneration() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b")
        class TypeB : BaseType()
      }
    """
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(source)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
        kspArgs[OPTION_GENERATE_PROGUARD_RULES] = "false"
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    assertThat(result.generatedFiles.filter { it.extension == "pro" }).isEmpty()
  }

  @Test
  fun duplicateLabels() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        class TypeA : BaseType()
        @TypeLabel("a")
        class TypeB : BaseType()
      }
    """
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(source)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Duplicate label")
  }

  @Test
  fun duplicateAlternateLabels() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a", alternateLabels = ["aa"])
        class TypeA : BaseType()
        @TypeLabel("b", alternateLabels = ["aa"])
        class TypeB : BaseType()
      }
    """
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(source)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Duplicate alternate label")
  }

  @Test
  fun genericSubTypes() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType<T> {
        @TypeLabel("a")
        class TypeA : BaseType<String>()
        @TypeLabel("b")
        class TypeB<T> : BaseType<T>()
      }
    """
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(source)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
    assertThat(result.messages).contains("Moshi-sealed subtypes cannot be generic.")
  }

  @Test
  fun objectAdapters() {
    val source =
      kotlin(
        "BaseType.kt",
        """
      package test
      import com.squareup.moshi.JsonClass
      import dev.zacsweers.moshix.sealed.annotations.TypeLabel

      @JsonClass(generateAdapter = true, generator = "sealed:type")
      sealed class BaseType {
        @TypeLabel("a")
        object TypeA : BaseType()
        @TypeLabel("b")
        object TypeB : BaseType()
      }
    """
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(source)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
      }
    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedFile = File(generatedSourcesDir, "kotlin/test/BaseTypeJsonAdapter.kt")
    assertThat(generatedFile.exists()).isTrue()
    // language=kotlin
    assertThat(generatedFile.readText().trim())
      .isEqualTo(
        """
      // Code generated by moshi-sealed. Do not edit.
      package test

      import com.squareup.moshi.JsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.Moshi
      import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
      import com.squareup.moshi.addAdapter
      import dev.zacsweers.moshix.`sealed`.runtime.`internal`.ObjectJsonAdapter
      import kotlin.ExperimentalStdlibApi
      import kotlin.OptIn
      import kotlin.Suppress
      import kotlin.Unit
      import kotlin.collections.emptySet

      @Suppress("DEPRECATION", "unused", "ClassName", "REDUNDANT_PROJECTION", "RedundantExplicitType",
          "LocalVariableName", "RedundantVisibilityModifier")
      public class BaseTypeJsonAdapter(
        moshi: Moshi,
      ) : JsonAdapter<BaseType>() {
        @Suppress("UNCHECKED_CAST")
        @OptIn(ExperimentalStdlibApi::class)
        private val runtimeAdapter: JsonAdapter<BaseType> =
            PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
              .withSubtype(BaseType.TypeA::class.java, "a")
              .withSubtype(BaseType.TypeB::class.java, "b")
              .create(BaseType::class.java, emptySet(), moshi.newBuilder()
                .addAdapter<BaseType.TypeA>(ObjectJsonAdapter(BaseType.TypeA))
            .addAdapter<BaseType.TypeB>(ObjectJsonAdapter(BaseType.TypeB))
            .build()) as JsonAdapter<BaseType>


        public override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)

        public override fun toJson(writer: JsonWriter, value_: BaseType?): Unit {
          runtimeAdapter.toJson(writer, value_)
        }
      }
      """.trimIndent()
      )
  }

  @Test
  fun separateFiles() {
    val base =
      kotlin(
        "BaseType.kt",
        """
        package test

        import com.squareup.moshi.JsonClass

        @JsonClass(generateAdapter = true, generator = "sealed:type")
        sealed interface BaseType
      """
      )

    val subType =
      kotlin(
        "SubType.kt",
        """
        package test

        import com.squareup.moshi.JsonClass
        import dev.zacsweers.moshix.sealed.annotations.TypeLabel

        @JsonClass(generateAdapter = true)
        @TypeLabel("a")
        data class SubType(val foo: String): BaseType"""
      )

    val compilation =
      KotlinCompilation().apply {
        sources = listOf(base, subType)
        inheritClassPath = true
        symbolProcessorProviders = listOf(MoshiSealedSymbolProcessorProvider())
      }

    val result = compilation.compile()
    assertThat(result.exitCode).isEqualTo(ExitCode.OK)

    val generatedSourcesDir = compilation.kspSourcesDir
    val generatedAdapter = File(generatedSourcesDir, "kotlin/test/BaseTypeJsonAdapter.kt")
    assertThat(generatedAdapter.exists()).isTrue()

    // language=kotlin
    assertThat(generatedAdapter.readText().trim())
      .isEqualTo(
        """
      // Code generated by moshi-sealed. Do not edit.
      package test

      import com.squareup.moshi.JsonAdapter
      import com.squareup.moshi.JsonReader
      import com.squareup.moshi.JsonWriter
      import com.squareup.moshi.Moshi
      import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
      import kotlin.Suppress
      import kotlin.Unit
      import kotlin.collections.emptySet

      @Suppress("DEPRECATION", "unused", "ClassName", "REDUNDANT_PROJECTION", "RedundantExplicitType",
          "LocalVariableName", "RedundantVisibilityModifier")
      public class BaseTypeJsonAdapter(
        moshi: Moshi,
      ) : JsonAdapter<BaseType>() {
        @Suppress("UNCHECKED_CAST")
        private val runtimeAdapter: JsonAdapter<BaseType> =
            PolymorphicJsonAdapterFactory.of(BaseType::class.java, "type")
              .withSubtype(SubType::class.java, "a")
              .create(BaseType::class.java, emptySet(), moshi) as JsonAdapter<BaseType>


        public override fun fromJson(reader: JsonReader): BaseType? = runtimeAdapter.fromJson(reader)

        public override fun toJson(writer: JsonWriter, value_: BaseType?): Unit {
          runtimeAdapter.toJson(writer, value_)
        }
      }
      """.trimIndent()
      )
  }
}
