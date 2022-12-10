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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.InitNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextNamespaceBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextNamespaceBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "_PyNamespace_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyNamespaceNewNode extends PythonBuiltinNode {
        @Specialization
        public Object impDict(PDict dict,
                        @Shared("getIt") @Cached HashingStorageGetIterator getIterator,
                        @Shared("itNext") @Cached HashingStorageIteratorNext itNext,
                        @Shared("itKey") @Cached HashingStorageIteratorKey itKey,
                        @Shared("itVal") @Cached HashingStorageIteratorValue itValue,
                        @Shared("dylib") @CachedLibrary(limit = "1") DynamicObjectLibrary dyLib,
                        @Shared("transform") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            HashingStorage storage = dict.getDictStorage();
            return impl(storage, getIterator, itNext, itKey, itValue, dyLib, transformExceptionToNativeNode);
        }

        private Object impl(HashingStorage storage, HashingStorageGetIterator getIterator, HashingStorageIteratorNext itNext,
                        HashingStorageIteratorKey itKey, HashingStorageIteratorValue itValue,
                        DynamicObjectLibrary dyLib, TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                PSimpleNamespace ns = factory().createSimpleNamespace();
                HashingStorageNodes.HashingStorageIterator it = getIterator.execute(storage);
                while (itNext.execute(storage, it)) {
                    Object key = itKey.execute(storage, it);
                    Object value = itValue.execute(storage, it);
                    dyLib.put(ns, assertNoJavaString(key), value);
                }
                return ns;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!isDict(dict)")
        public Object impGeneric(VirtualFrame frame, Object dict,
                        @Cached InitNode initNode,
                        @Shared("getIt") @Cached HashingStorageGetIterator getIterator,
                        @Shared("itNext") @Cached HashingStorageIteratorNext itNext,
                        @Shared("itKey") @Cached HashingStorageIteratorKey itKey,
                        @Shared("itVal") @Cached HashingStorageIteratorValue itValue,
                        @Shared("dylib") @CachedLibrary(limit = "1") DynamicObjectLibrary dyLib,
                        @Shared("transform") @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                HashingStorage hs = initNode.execute(frame, dict, PKeyword.EMPTY_KEYWORDS);
                return impl(hs, getIterator, itNext, itKey, itValue, dyLib, transformExceptionToNativeNode);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

}
