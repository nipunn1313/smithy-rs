/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.rust.codegen.server.smithy

import java.util.logging.Level
import java.util.logging.Logger
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.build.SmithyBuildPlugin
import software.amazon.smithy.codegen.core.ReservedWordSymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.rust.codegen.rustlang.RustReservedWordSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.BaseSymbolMetadataProvider
import software.amazon.smithy.rust.codegen.smithy.DefaultConfig
import software.amazon.smithy.rust.codegen.smithy.EventStreamSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.StreamingShapeMetadataProvider
import software.amazon.smithy.rust.codegen.smithy.StreamingShapeSymbolProvider
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitor
import software.amazon.smithy.rust.codegen.smithy.SymbolVisitorConfig
import software.amazon.smithy.rust.codegen.smithy.customize.CombinedCodegenDecorator

class RustCodegenPlugin : SmithyBuildPlugin {
    private val logger = Logger.getLogger(javaClass.name)

    override fun getName(): String = "rust-server-codegen"

    override fun execute(context: PluginContext) {
        // Suppress extremely noisy logs about reserved words
        Logger.getLogger(ReservedWordSymbolProvider::class.java.name).level = Level.OFF
        val codegenDecorator = CombinedCodegenDecorator.fromClasspath(context)
        CodegenVisitor(context, codegenDecorator).execute()
    }

    companion object {
        fun baseSymbolProvider(
                model: Model,
                serviceShape: ServiceShape,
                symbolVisitorConfig: SymbolVisitorConfig = DefaultConfig
        ) =
                SymbolVisitor(model, serviceShape = serviceShape, config = symbolVisitorConfig)
                        .let {
                            EventStreamSymbolProvider(symbolVisitorConfig.runtimeConfig, it, model)
                        }
                        .let { StreamingShapeSymbolProvider(it, model) }
                        .let { BaseSymbolMetadataProvider(it) }
                        .let { StreamingShapeMetadataProvider(it, model) }
                        .let { RustReservedWordSymbolProvider(it) }
    }
}