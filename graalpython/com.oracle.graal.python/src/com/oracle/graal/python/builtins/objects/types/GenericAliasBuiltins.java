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
package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ORIGIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIGIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIG_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSCHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DEEPCOPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectDir;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenericAlias)
public class GenericAliasBuiltins extends PythonBuiltins {
    private static final TruffleString[] ATTR_EXCEPTIONS = {T___ORIGIN__, T___ARGS__, T___PARAMETERS__, T___MRO_ENTRIES__, T___REDUCE_EX__, T___REDUCE__, T___COPY__, T___DEEPCOPY__};

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GenericAliasBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ORIGIN__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class OriginNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object origin(PGenericAlias self) {
            return self.getOrigin();
        }
    }

    @Builtin(name = J___ARGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object args(PGenericAlias self) {
            return self.getArgs();
        }
    }

    @Builtin(name = J___PARAMETERS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ParametersNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object parameters(PGenericAlias self) {
            if (self.getParameters() == null) {
                self.setParameters(factory().createTuple(GenericTypeNodes.makeParameters(self.getArgs())));
            }
            return self.getParameters();
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object union(Object self, Object other,
                        @Cached GenericTypeNodes.UnionTypeOrNode orNode) {
            return orNode.execute(self, other);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString SEPARATOR = tsLiteral(", ");

        @Specialization
        @TruffleBoundary
        Object repr(PGenericAlias self) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            reprItem(sb, self.getOrigin());
            sb.appendCodePointUncached('[');
            SequenceStorage argsStorage = self.getArgs().getSequenceStorage();
            for (int i = 0; i < argsStorage.length(); i++) {
                if (i > 0) {
                    sb.appendStringUncached(SEPARATOR);
                }
                reprItem(sb, argsStorage.getItemNormalized(i));
            }
            if (argsStorage.length() == 0) {
                // for something like tuple[()] we should print a "()"
                sb.appendCodePointUncached('(');
                sb.appendCodePointUncached(')');
            }
            sb.appendCodePointUncached(']');
            return sb.toStringUncached();
        }

        // Equivalent of ga_repr_item in CPython
        private static void reprItem(TruffleStringBuilder sb, Object obj) {
            if (obj == PEllipsis.INSTANCE) {
                sb.appendStringUncached(StringLiterals.T_ELLIPSIS);
                return;
            }
            GenericTypeNodes.reprItem(sb, obj);
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        long hash(VirtualFrame frame, PGenericAlias self,
                        @Cached PyObjectHashNode hashOrigin,
                        @Cached PyObjectHashNode hashArgs) {
            long h0 = hashOrigin.execute(frame, self.getOrigin());
            long h1 = hashArgs.execute(frame, self.getArgs());
            return h0 ^ h1;
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class CallMethodNode extends PythonVarargsBuiltinNode {
        @Specialization
        Object call(VirtualFrame frame, PGenericAlias self, Object[] args, PKeyword[] kwargs,
                        @Cached CallNode callNode,
                        @Cached PyObjectSetAttr setAttr,
                        @Cached IsBuiltinClassProfile typeErrorProfile,
                        @Cached IsBuiltinClassProfile attributeErrorProfile) {
            Object result = callNode.execute(frame, self.getOrigin(), args, kwargs);
            try {
                setAttr.execute(frame, result, T___ORIG_CLASS__, self);
            } catch (PException e) {
                if (!typeErrorProfile.profileException(e, TypeError) && !attributeErrorProfile.profileException(e, PythonBuiltinClassType.AttributeError)) {
                    throw e;
                }
            }
            return result;
        }
    }

    @Builtin(name = J___GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetAttributeNode extends PythonBinaryBuiltinNode {

        @Specialization
        @ExplodeLoop
        Object getattribute(VirtualFrame frame, PGenericAlias self, Object nameObj,
                        @Cached CastToTruffleStringNode cast,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached ObjectBuiltins.GetAttributeNode genericGetAttribute) {
            TruffleString name;
            try {
                name = cast.execute(nameObj);
            } catch (CannotCastException e) {
                return genericGetAttribute.execute(frame, self, nameObj);
            }
            for (int i = 0; i < ATTR_EXCEPTIONS.length; i++) {
                if (equalNode.execute(name, ATTR_EXCEPTIONS[i], TS_ENCODING)) {
                    return genericGetAttribute.execute(frame, self, nameObj);
                }
            }
            return getAttr.execute(frame, self.getOrigin(), name);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eq(VirtualFrame frame, PGenericAlias self, PGenericAlias other,
                        @Cached PyObjectRichCompareBool.EqNode eqOrigin,
                        @Cached PyObjectRichCompareBool.EqNode eqArgs) {
            return eqOrigin.execute(frame, self.getOrigin(), other.getOrigin()) && eqArgs.execute(frame, self.getArgs(), other.getArgs());
        }

        @Fallback
        @SuppressWarnings("unused")
        Object eq(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___MRO_ENTRIES__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MroEntriesNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object mro(PGenericAlias self, @SuppressWarnings("unused") Object bases) {
            return factory().createTuple(new Object[]{self.getOrigin()});
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object check(PGenericAlias self, Object other) {
            throw raise(TypeError, ErrorMessages.ISINSTANCE_ARG_2_CANNOT_BE_A_PARAMETERIZED_GENERIC);
        }
    }

    @Builtin(name = J___SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object check(PGenericAlias self, Object other) {
            throw raise(TypeError, ErrorMessages.ISSUBCLASS_ARG_2_CANNOT_BE_A_PARAMETERIZED_GENERIC);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PGenericAlias self,
                        @Cached GetClassNode getClassNode) {
            Object args = factory().createTuple(new Object[]{self.getOrigin(), self.getArgs()});
            return factory().createTuple(new Object[]{getClassNode.execute(self), args});
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object dir(PGenericAlias self,
                        @Cached PyObjectDir dir,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached ListNodes.AppendNode appendNode) {
            PList list = dir.execute(null, self.getOrigin());
            for (int i = 0; i < ATTR_EXCEPTIONS.length; i++) {
                if (!containsNode.execute(null, list, ATTR_EXCEPTIONS[i])) {
                    appendNode.execute(list, ATTR_EXCEPTIONS[i]);
                }
            }
            return list;
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getitem(PGenericAlias self, Object item) {
            if (self.getParameters() == null) {
                self.setParameters(factory().createTuple(GenericTypeNodes.makeParameters(self.getArgs())));
            }
            Object[] newargs = GenericTypeNodes.subsParameters(this, self, self.getArgs(), self.getParameters(), item);
            PTuple newargsTuple = factory().createTuple(newargs);
            return factory().createGenericAlias(self.getOrigin(), newargsTuple);
        }
    }
}
