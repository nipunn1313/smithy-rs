/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.core.smithy.customize

import software.amazon.smithy.rust.codegen.core.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.core.rustlang.Writable
import software.amazon.smithy.rust.codegen.core.rustlang.writable

/**
 * An overrideable section for code generation. Usage:
 * ```kotlin
 * sealed class OperationCustomization(name: String) : Section(name) {
 *      // Sections can be state-carrying to allow implementations to make different choices based on
 *      // different operations
 *      data class RequestCreation(protocolConfig: ProtocolConfig, operation: OperationShape) : Section("RequestCreation")
 *      // Sections can be stateless, e.g. this section that could write code into the
 *      // top level operation module
 *      object OperationModule : ServiceConfig("OperationTopLevel")
 * }
 */
abstract class Section(val name: String)

/**
 * Detatched section abstraction to allow adhoc sections to be created. By using the `.writer` method, a instantiation
 * of this section can be easily created.
 */
abstract class AdHocSection<T : Section>(val name: String) {
    /**
     * Helper to enable easily combining detached sections with the [CoreCodegenDecorator.extraSections] method.
     */
    fun create(w: (T) -> Writable): Pair<AdHocSection<*>, (Section) -> Writable> = this to { s: Section ->
        w((s as T))
    }
}

/**
 * A named section generator allows customization via a predefined set of named sections.
 *
 * Implementors MUST override section and use a `when` clause to handle each section individually
 */
abstract class NamedSectionGenerator<T : Section> {
    abstract fun section(section: T): Writable
    protected val emptySection = writable { }
}

/** Convenience for rendering a list of customizations for a given section */
fun <T : Section> RustWriter.writeCustomizations(customizations: List<NamedSectionGenerator<T>>, section: T) {
    for (customization in customizations) {
        customization.section(section)(this)
    }
}
