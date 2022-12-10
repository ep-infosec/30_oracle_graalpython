/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cell;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltinsFactory.GetRefNodeGen;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCell)
public class CellBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CellBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        public boolean eq(VirtualFrame frame, PCell self, PCell other,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ConditionProfile nonEmptyProfile,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (nonEmptyProfile.profile(left != null && right != null)) {
                return eqNode.execute(frame, left, right);
            }
            return left == null && right == null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___EQ__, "cell", self);
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBuiltinNode {
        @Specialization
        public boolean ne(VirtualFrame frame, PCell self, PCell other,
                        @Cached PyObjectRichCompareBool.NeNode neNode,
                        @Cached ConditionProfile nonEmptyProfile,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (nonEmptyProfile.profile(left != null && right != null)) {
                return neNode.execute(frame, left, right);
            }
            return left != null || right != null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___NE__, "cell", self);
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends PythonBuiltinNode {
        @Specialization
        public boolean lt(VirtualFrame frame, PCell self, PCell other,
                        @Cached PyObjectRichCompareBool.LtNode ltNode,
                        @Cached ConditionProfile nonEmptyProfile,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (nonEmptyProfile.profile(left != null && right != null)) {
                return ltNode.execute(frame, left, right);
            }
            return right != null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___LT__, "cell", self);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends PythonBuiltinNode {
        @Specialization
        public boolean le(VirtualFrame frame, PCell self, PCell other,
                        @Cached PyObjectRichCompareBool.LeNode leNode,
                        @Cached ConditionProfile nonEmptyProfile,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (nonEmptyProfile.profile(left != null && right != null)) {
                return leNode.execute(frame, left, right);
            }
            return left == null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___LE__, "cell", self);
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends PythonBuiltinNode {
        @Specialization
        public boolean gt(VirtualFrame frame, PCell self, PCell other,
                        @Cached PyObjectRichCompareBool.GtNode gtNode,
                        @Cached ConditionProfile nonEmptyProfile,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (nonEmptyProfile.profile(left != null && right != null)) {
                return gtNode.execute(frame, left, right);
            }
            return left != null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___GT__, "cell", self);
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends PythonBuiltinNode {
        @Specialization
        public boolean ge(VirtualFrame frame, PCell self, PCell other,
                        @Cached PyObjectRichCompareBool.GeNode geNode,
                        @Cached ConditionProfile nonEmptyProfile,
                        @Cached GetRefNode getRefL,
                        @Cached GetRefNode getRefR) {
            Object left = getRefL.execute(self);
            Object right = getRefR.execute(other);
            if (nonEmptyProfile.profile(left != null && right != null)) {
                return geNode.execute(frame, left, right);
            }
            return right == null;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object notImplemented(Object self, Object other) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, T___GE__, "cell", self);
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        static TruffleString repr(PCell self,
                        @Cached GetRefNode getRef,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object ref = getRef.execute(self);
            if (ref == null) {
                return simpleTruffleStringFormatNode.format("<cell at 0x%s: empty>", PythonAbstractObject.systemHashCodeAsHexString(self));
            }
            TruffleString typeName = getNameNode.execute(getClassNode.execute(ref));
            return simpleTruffleStringFormatNode.format("<cell at 0x%s: %s object at 0x%s>", PythonAbstractObject.systemHashCodeAsHexString(self), typeName,
                            PythonAbstractObject.systemHashCodeAsHexString(ref));
        }

        @Fallback
        public Object eq(Object self) {
            if (self instanceof PCell) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__repr__", "cell", self);
        }
    }

    @Builtin(name = "cell_contents", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class CellContentsNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(PCell self, @SuppressWarnings("unused") PNone none,
                        @Cached GetRefNode getRef) {
            Object ref = getRef.execute(self);
            if (ref == null) {
                throw raise(ValueError, ErrorMessages.IS_EMPTY, "Cell");
            }
            return ref;
        }

        @Specialization(guards = "isDeleteMarker(marker)")
        Object delete(PCell self, @SuppressWarnings("unused") Object marker) {
            self.clearRef();
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(ref)", "!isDeleteMarker(ref)"})
        Object set(PCell self, Object ref) {
            self.setRef(ref);
            return PNone.NONE;
        }
    }

    public abstract static class GetRefNode extends PNodeWithContext {
        public abstract Object execute(PCell self);

        @Specialization(guards = {"isSingleContext()", "self == cachedSelf"}, assumptions = {"cachedSelf.isEffectivelyFinalAssumption()"}, limit = "1")
        Object cached(@SuppressWarnings("unused") PCell self,
                        @SuppressWarnings("unused") @Cached("self") PCell cachedSelf,
                        @Cached("self.getRef()") Object ref) {
            return ref;
        }

        @Specialization(replaces = "cached")
        Object uncached(PCell self) {
            return self.getRef();
        }

        public static GetRefNode create() {
            return GetRefNodeGen.create();
        }
    }

}
