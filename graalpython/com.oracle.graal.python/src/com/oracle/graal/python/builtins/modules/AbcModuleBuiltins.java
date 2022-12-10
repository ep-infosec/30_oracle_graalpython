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
package com.oracle.graal.python.builtins.modules;

import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.COLLECTION_FLAGS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

@CoreFunctions(defineModule = "_abc")
public class AbcModuleBuiltins extends PythonBuiltins {

    private static TruffleString ABC_TPFLAGS = toTruffleStringUncached("__abc_tpflags__");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbcModuleBuiltinsFactory.getFactories();
    }

    // the part of _abc.c/_abc_init() which handles the COLLECTION_FLAGS
    @Builtin(name = "_abc_init", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AbcInitCollectionFlagsNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object init(Object object,
                        @Cached DeleteAttributeNode deleteAttributeNode) {
            TypeNodes.IsTypeNode isTypeNode = TypeNodes.IsTypeNode.getUncached();
            if (isTypeNode.execute(object)) {
                Object flags = PyObjectLookupAttr.getUncached().execute(null, object, ABC_TPFLAGS);
                long val;
                try {
                    val = CastToJavaLongLossyNode.getUncached().execute(flags);
                } catch (CannotCastException ex) {
                    return PNone.NONE;
                }
                if ((val & COLLECTION_FLAGS) == COLLECTION_FLAGS) {
                    throw raise(TypeError, ErrorMessages.ABC_FLAGS_CANNOT_BE_SEQUENCE_AND_MAPPING);
                }
                long tpFlags = TypeNodes.GetTypeFlagsNode.getUncached().execute(object);
                tpFlags |= (val & COLLECTION_FLAGS);
                WriteAttributeToObjectNode.getUncached().execute(object, TYPE_FLAGS, tpFlags);
                deleteAttributeNode.execute(null, object, ABC_TPFLAGS);
            }
            return PNone.NONE;
        }
    }

    // the part of _abc.c/_abc_register() which handles the COLLECTION_FLAGS
    @Builtin(name = "_abc_register", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RegisterCollectionFlagsNode extends PythonBinaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        static Object register(Object self, Object subclass) {
            TypeNodes.IsTypeNode isTypeNode = TypeNodes.IsTypeNode.getUncached();
            if (isTypeNode.execute(self)) {
                TypeNodes.GetTypeFlagsNode getFlagsNode = TypeNodes.GetTypeFlagsNode.getUncached();
                long tpFlags = getFlagsNode.execute(self);
                long collectionFlag = tpFlags & COLLECTION_FLAGS;
                if (collectionFlag > 0) {
                    setCollectionFlagRecursive(subclass, collectionFlag, getFlagsNode, WriteAttributeToObjectNode.getUncached(), TypeNodes.GetSubclassesNode.getUncached(), isTypeNode);
                }
            }
            return PNone.NONE;
        }

        private static void setCollectionFlagRecursive(Object child, long flag, TypeNodes.GetTypeFlagsNode getFlags, WriteAttributeToObjectNode writeHiddenFlagsNode,
                        TypeNodes.GetSubclassesNode getSubclassesNode, TypeNodes.IsTypeNode isTypeNode) {
            assert flag == TypeFlags.SEQUENCE || flag == TypeFlags.MAPPING : flag;
            long tpFlags = getFlags.execute(child);
            tpFlags &= ~COLLECTION_FLAGS;
            tpFlags |= flag;
            writeHiddenFlagsNode.execute(child, TYPE_FLAGS, tpFlags);
            Set<PythonAbstractClass> grandchildren = getSubclassesNode.execute(child);
            for (PythonAbstractClass c : grandchildren) {
                if (isTypeNode.execute(c)) {
                    setCollectionFlagRecursive(c, flag, getFlags, writeHiddenFlagsNode, getSubclassesNode, isTypeNode);
                }
            }
        }
    }
}
