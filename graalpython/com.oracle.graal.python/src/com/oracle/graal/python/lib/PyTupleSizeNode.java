/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_OBJECT_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateUncached
public abstract class PyTupleSizeNode extends PNodeWithContext {
    public abstract int execute(Object tuple);

    @Specialization
    static int size(PTuple tuple) {
        return tuple.getSequenceStorage().length();
    }

    @Specialization(guards = "isTupleSubtype(tuple, getClassNode, isSubtypeNode)", limit = "1")
    static int sizeNative(PythonAbstractNativeObject tuple,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                    @Cached CExtNodes.PCallCapiFunction callSizeNode,
                    @Cached CExtNodes.ToSulongNode toSulongNode,
                    @Cached CastToJavaIntLossyNode asIntNode) {
        try {
            return asIntNode.execute(callSizeNode.call(FUN_PY_TRUFFLE_OBJECT_SIZE, toSulongNode.execute(tuple)));
        } catch (CannotCastException e) {
            throw CompilerDirectives.shouldNotReachHere("Failed to cast result of PyTruffle_Object_Size");
        }
    }

    @SuppressWarnings("unused")
    @Fallback
    static int size(Object obj,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC_S, "PyTuple_Size");
    }

    protected boolean isTupleSubtype(Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
        return isSubtypeNode.execute(getClassNode.execute(obj), PythonBuiltinClassType.PTuple);
    }
}
