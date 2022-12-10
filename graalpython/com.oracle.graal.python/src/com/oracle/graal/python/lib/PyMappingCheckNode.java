/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.deque.PDeque;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;

/**
 * Equivalent of CPython's {@code PyMapping_Check}.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyMappingCheckNode extends PNodeWithContext {
    public abstract boolean execute(Object object);

    @Specialization
    static boolean doDict(@SuppressWarnings("unused") PDict object) {
        return true;
    }

    @Specialization
    static boolean doString(@SuppressWarnings("unused") TruffleString object) {
        return true;
    }

    @Specialization
    static boolean doSequence(@SuppressWarnings("unused") PSequence object) {
        return true;
    }

    @Specialization
    static boolean doArray(@SuppressWarnings("unused") PArray object) {
        return true;
    }

    @Specialization
    static boolean doMemoryView(@SuppressWarnings("unused") PMemoryView object) {
        return true;
    }

    @Specialization
    static boolean doMappingproxy(@SuppressWarnings("unused") PMappingproxy object) {
        return true;
    }

    @Specialization
    static boolean doRange(@SuppressWarnings("unused") PRange object) {
        return true;
    }

    @Specialization
    static boolean doDeque(@SuppressWarnings("unused") PDeque object) {
        return false;
    }

    @Specialization
    static boolean doSet(@SuppressWarnings("unused") PBaseSet object) {
        return false;
    }

    protected static boolean cannotBeMapping(Object object) {
        return object instanceof PDeque || object instanceof PBaseSet;
    }

    protected static boolean isKnownMapping(Object object) {
        return object instanceof PDict || isJavaString(object) || object instanceof TruffleString || object instanceof PSequence || object instanceof PArray ||
                        object instanceof PMemoryView || object instanceof PRange || object instanceof PMappingproxy;
    }

    @Specialization(guards = {"!isKnownMapping(object)", "!cannotBeMapping(object)"})
    boolean doPythonObject(PythonObject object,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("lookupGetItem") @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupGetItem) {
        Object type = getClassNode.execute(object);
        return lookupGetItem.execute(type) != PNone.NO_VALUE;
    }

    @Specialization(guards = {"!isKnownMapping(object)", "!cannotBeMapping(object)"}, replaces = "doPythonObject")
    boolean doGeneric(Object object,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Shared("lookupGetItem") @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupGetItem,
                    @CachedLibrary(limit = "3") InteropLibrary lib) {
        Object type = getClassNode.execute(object);
        if (type == PythonBuiltinClassType.ForeignObject) {
            return lib.hasHashEntries(object);
        }
        return lookupGetItem.execute(type) != PNone.NO_VALUE;
    }

    public static PyMappingCheckNode create() {
        return PyMappingCheckNodeGen.create();
    }

    public static PyMappingCheckNode getUncached() {
        return PyMappingCheckNodeGen.getUncached();
    }
}
