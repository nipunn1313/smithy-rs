/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rustsdk

import software.amazon.smithy.rust.codegen.client.smithy.ClientCodegenContext
import software.amazon.smithy.rust.codegen.client.smithy.customize.ClientCodegenDecorator
import software.amazon.smithy.rust.codegen.client.smithy.generators.config.ConfigCustomization
import software.amazon.smithy.rust.codegen.client.smithy.generators.config.ServiceConfig
import software.amazon.smithy.rust.codegen.core.rustlang.RustModule
import software.amazon.smithy.rust.codegen.core.rustlang.Writable
import software.amazon.smithy.rust.codegen.core.rustlang.join
import software.amazon.smithy.rust.codegen.core.rustlang.rust
import software.amazon.smithy.rust.codegen.core.rustlang.rustTemplate
import software.amazon.smithy.rust.codegen.core.rustlang.writable
import software.amazon.smithy.rust.codegen.core.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.core.smithy.RustCrate
import software.amazon.smithy.rust.codegen.core.smithy.customize.AdHocSection
import software.amazon.smithy.rust.codegen.core.smithy.customize.Section

/**
 * Section enabling linkage between `SdkConfig` and <service>::Config
 */
object SdkConfigSection : AdHocSection<SdkConfigSection.CopySdkConfigToClientConfig>("SdkConfig") {
    /**
     * [sdkConfig]: A reference to the SDK config struct
     * [serviceConfigBuilder]: A reference (owned) to the `<service>::config::Builder` struct.
     *
     * Each invocation of this section MUST be a complete statement (ending with a semicolon), e.g:
     * ```
     * rust("${section.serviceConfigBuilder}.set_foo(${section.sdkConfig}.foo());")
     * ```
     */
    data class CopySdkConfigToClientConfig(val sdkConfig: String, val serviceConfigBuilder: String) :
        Section("CopyConfig")

    /**
     * Copy a field from SDK config to service config with an optional map block.
     *
     * This handles the common case where the field name is identical in both cases and an accessor is used.
     *
     * # Examples
     * ```kotlin
     * SdkConfigSection.copyField("some_string_field") { rust("|s|s.to_to_string()") }
     * ```
     */
    fun copyField(fieldName: String, map: Writable?) = SdkConfigSection.create { section ->
        {
            val mapBlock = map?.let { writable { rust(".map(#W)", it) } } ?: writable { }
            rustTemplate(
                "${section.serviceConfigBuilder}.set_$fieldName(${section.sdkConfig}.$fieldName()#{map});",
                "map" to mapBlock,
            )
        }
    }
}

/**
 * SdkConfig -> <service>::Config for settings that come from generic smithy
 */
class GenericSmithySdkConfigSettings : ClientCodegenDecorator {
    override val name: String = "GenericSmithySdkConfigSettings"
    override val order: Byte = 0

    override fun extraSections(codegenContext: ClientCodegenContext): List<Pair<AdHocSection<*>, (Section) -> Writable>> =
        listOf(
            SdkConfigSection.create { section ->
                writable {
                    rust(
                        """
                        // resiliency
                        ${section.serviceConfigBuilder}.set_retry_config(${section.sdkConfig}.retry_config().cloned());
                        ${section.serviceConfigBuilder}.set_timeout_config(${section.sdkConfig}.timeout_config().cloned());
                        ${section.serviceConfigBuilder}.set_sleep_impl(${section.sdkConfig}.sleep_impl());

                        ${section.serviceConfigBuilder}.set_http_connector(${section.sdkConfig}.http_connector().cloned());

                        """,
                    )
                }
            },
        )
}

/**
 * Adds functionality for constructing `<service>::Config` objects from `aws_types::SdkConfig`s
 *
 * - `From<&aws_types::SdkConfig> for <service>::config::Builder`: Enabling customization
 * - `pub fn new(&aws_types::SdkConfig) -> <service>::Config`: Direct construction without customization
 */
class SdkConfigDecorator : ClientCodegenDecorator {
    override val name: String = "SdkConfig"
    override val order: Byte = 0

    override fun configCustomizations(
        codegenContext: ClientCodegenContext,
        baseCustomizations: List<ConfigCustomization>,
    ): List<ConfigCustomization> {
        return baseCustomizations + NewFromShared(codegenContext.runtimeConfig)
    }

    override fun extras(codegenContext: ClientCodegenContext, rustCrate: RustCrate) {
        val codegenScope = arrayOf(
            "SdkConfig" to AwsRuntimeType.awsTypes(codegenContext.runtimeConfig).resolve("sdk_config::SdkConfig"),
        )
        rustCrate.withModule(RustModule.Config) {
            rustTemplate(
                """
                impl From<&#{SdkConfig}> for Builder {
                    fn from(input: &#{SdkConfig}) -> Self {
                        let mut builder = Builder::default();
                        #{augmentBuilder}


                        builder
                    }
                }

                impl From<&#{SdkConfig}> for Config {
                    fn from(sdk_config: &#{SdkConfig}) -> Self {
                        Builder::from(sdk_config).build()
                    }
                }
                """,
                "augmentBuilder" to codegenContext.rootDecorator.extraSections(codegenContext)
                    .filter { (t, _) -> t is SdkConfigSection }.map { (_, sectionWriter) ->
                        sectionWriter(
                            SdkConfigSection.CopySdkConfigToClientConfig(
                                sdkConfig = "input",
                                serviceConfigBuilder = "builder",
                            ),
                        )
                    }.join("\n"),
                *codegenScope,
            )
        }
    }
}

class NewFromShared(runtimeConfig: RuntimeConfig) : ConfigCustomization() {
    private val codegenScope = arrayOf(
        "SdkConfig" to AwsRuntimeType.awsTypes(runtimeConfig).resolve("sdk_config::SdkConfig"),
    )

    override fun section(section: ServiceConfig): Writable {
        return when (section) {
            ServiceConfig.ConfigImpl -> writable {
                rustTemplate(
                    """
                    /// Creates a new [service config](crate::Config) from a [shared `config`](#{SdkConfig}).
                    pub fn new(config: &#{SdkConfig}) -> Self {
                        Builder::from(config).build()
                    }
                    """,
                    *codegenScope,
                )
            }

            else -> emptySection
        }
    }
}
