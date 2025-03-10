/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.python.smithy

import software.amazon.smithy.rust.codegen.core.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.core.smithy.RuntimeType

/**
 * Object used *exclusively* in the runtime of the Python server, for separation concerns.
 * Analogous to the companion object in [RuntimeType] and [software.amazon.smithy.rust.codegen.server.smithy.ServerRuntimeType]; see its documentation for details.
 * For a runtime type that is used in the client, or in both the client and the server, use [RuntimeType] directly.
 */
object PythonServerRuntimeType {
    fun blob(runtimeConfig: RuntimeConfig) =
        PythonServerCargoDependency.smithyHttpServerPython(runtimeConfig).toType().resolve("types::Blob")

    fun byteStream(runtimeConfig: RuntimeConfig) =
        PythonServerCargoDependency.smithyHttpServerPython(runtimeConfig).toType().resolve("types::ByteStream")

    fun dateTime(runtimeConfig: RuntimeConfig) =
        PythonServerCargoDependency.smithyHttpServerPython(runtimeConfig).toType().resolve("types::DateTime")
}
