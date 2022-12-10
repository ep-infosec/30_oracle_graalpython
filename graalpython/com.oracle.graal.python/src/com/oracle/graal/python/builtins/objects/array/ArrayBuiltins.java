/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.array;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ARRAY;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_LBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACKET;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_SINGLE_QUOTE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltinsClinicProviders.ReduceExNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PArray)
public class ArrayBuiltins extends PythonBuiltins {

    public static final TruffleString T_ARRAY_RECONSTRUCTOR = tsLiteral("_array_reconstructor");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "left.getFormat() == right.getFormat()")
        Object concat(PArray left, PArray right) {
            try {
                int newLength = PythonUtils.addExact(left.getLength(), right.getLength());
                int itemsize = left.getFormat().bytesize;
                PArray newArray = factory().createArray(left.getFormatString(), left.getFormat(), newLength);
                PythonUtils.arraycopy(left.getBuffer(), 0, newArray.getBuffer(), 0, left.getLength() * itemsize);
                PythonUtils.arraycopy(right.getBuffer(), 0, newArray.getBuffer(), left.getLength() * itemsize, right.getLength() * itemsize);
                return newArray;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = "left.getFormat() != right.getFormat()")
        @SuppressWarnings("unused")
        Object error(PArray left, PArray right) {
            throw raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @Fallback
        Object error(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_APPEND_ARRAY_TO_ARRAY, right);
        }
    }

    @Builtin(name = J___IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object concat(VirtualFrame frame, PArray left, PArray right,
                        @Cached ExtendNode extendNode) {
            extendNode.execute(frame, left, right);
            return left;
        }

        @Fallback
        Object error(@SuppressWarnings("unused") Object left, Object right) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_EXTEND_ARRAY_WITH_ARRAY, right);
        }
    }

    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object concat(PArray self, int value) {
            try {
                int newLength = Math.max(PythonUtils.multiplyExact(self.getLength(), value), 0);
                int itemsize = self.getFormat().bytesize;
                PArray newArray = factory().createArray(self.getFormatString(), self.getFormat(), newLength);
                int segmentLength = self.getLength() * itemsize;
                for (int i = 0; i < value; i++) {
                    PythonUtils.arraycopy(self.getBuffer(), 0, newArray.getBuffer(), segmentLength * i, segmentLength);
                }
                return newArray;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.MulNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = J___IMUL__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class IMulNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object concat(PArray self, int value) {
            try {
                int newLength = Math.max(PythonUtils.multiplyExact(self.getLength(), value), 0);
                if (newLength != self.getLength()) {
                    self.checkCanResize(this);
                }
                int itemsize = self.getFormat().bytesize;
                int segmentLength = self.getLength() * itemsize;
                self.resize(newLength);
                for (int i = 0; i < value; i++) {
                    PythonUtils.arraycopy(self.getBuffer(), 0, self.getBuffer(), segmentLength * i, segmentLength);
                }
                return self;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.IMulNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(BufferFormat.class)
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"left.getFormat() == right.getFormat()", "!isFloatingPoint(left.getFormat())"})
        static boolean eqBytes(PArray left, PArray right) {
            if (left.getLength() != right.getLength()) {
                return false;
            }
            int itemsize = left.getFormat().bytesize;
            for (int i = 0; i < left.getLength() * itemsize; i++) {
                if (left.getBuffer()[i] != right.getBuffer()[i]) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = "left.getFormat() != right.getFormat()")
        static boolean eqItems(VirtualFrame frame, PArray left, PArray right,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getLeft,
                        @Cached ArrayNodes.GetValueNode getRight) {
            if (left.getLength() != right.getLength()) {
                return false;
            }
            for (int i = 0; i < left.getLength(); i++) {
                if (!eqNode.execute(frame, getLeft.execute(left, i), getRight.execute(right, i))) {
                    return false;
                }
            }
            return true;
        }

        // Separate specialization for float/double is needed because of NaN comparisons
        @Specialization(guards = {"left.getFormat() == right.getFormat()", "isFloatingPoint(left.getFormat())"})
        static boolean eqDoubles(PArray left, PArray right,
                        @Cached ArrayNodes.GetValueNode getLeft,
                        @Cached ArrayNodes.GetValueNode getRight) {
            if (left.getLength() != right.getLength()) {
                return false;
            }
            for (int i = 0; i < left.getLength(); i++) {
                double leftValue = (Double) getLeft.execute(left, i);
                double rightValue = (Double) getRight.execute(right, i);
                if (leftValue != rightValue) {
                    return false;
                }
            }
            return true;
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object eq(PArray left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean shouldCompareDouble(PArray left, PArray right) {
            return left.getFormat() == right.getFormat() && (left.getFormat() == BufferFormat.DOUBLE || left.getFormat() == BufferFormat.FLOAT);
        }

        protected static boolean shouldCompareBytes(PArray left, PArray right) {
            return left.getFormat() == right.getFormat() && left.getFormat() != BufferFormat.DOUBLE && left.getFormat() != BufferFormat.FLOAT;
        }
    }

    @ImportStatic(BufferFormat.class)
    abstract static class AbstractComparisonNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isFloatingPoint(left.getFormat()) || (left.getFormat() != right.getFormat())")
        boolean cmpItems(VirtualFrame frame, PArray left, PArray right,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached ArrayNodes.GetValueNode getLeft,
                        @Cached ArrayNodes.GetValueNode getRight) {
            int commonLength = Math.min(left.getLength(), right.getLength());
            for (int i = 0; i < commonLength; i++) {
                Object leftValue = getLeft.execute(left, i);
                Object rightValue = getRight.execute(right, i);
                if (!eqNode.execute(frame, leftValue, rightValue)) {
                    return coerceToBooleanNode.executeBoolean(frame, compareNode.executeObject(frame, leftValue, rightValue));
                }
            }
            return compareLengths(left.getLength(), right.getLength());
        }

        // Separate specialization for float/double is needed because of NaN comparisons
        @Specialization(guards = {"isFloatingPoint(left.getFormat())", "left.getFormat() == right.getFormat()"})
        boolean cmpDoubles(VirtualFrame frame, PArray left, PArray right,
                        @Cached("createComparison()") BinaryComparisonNode compareNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode coerceToBooleanNode,
                        @Cached ArrayNodes.GetValueNode getLeft,
                        @Cached ArrayNodes.GetValueNode getRight) {
            int commonLength = Math.min(left.getLength(), right.getLength());
            for (int i = 0; i < commonLength; i++) {
                double leftValue = (Double) getLeft.execute(left, i);
                double rightValue = (Double) getRight.execute(right, i);
                if (leftValue != rightValue) {
                    return coerceToBooleanNode.executeBoolean(frame, compareNode.executeObject(frame, leftValue, rightValue));
                }
            }
            return compareLengths(left.getLength(), right.getLength());
        }

        @Specialization(guards = "!isArray(right)")
        @SuppressWarnings("unused")
        static Object cmp(PArray left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        protected boolean compareLengths(int a, int b) {
            throw new AbstractMethodError("compareLengths");
        }

        protected BinaryComparisonNode createComparison() {
            throw new AbstractMethodError("createComparison");
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.LtNode.create();
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a < b;
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.GtNode.create();
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a > b;
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.LeNode.create();
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a <= b;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends AbstractComparisonNode {

        @Override
        protected BinaryComparisonNode createComparison() {
            return BinaryComparisonNode.GeNode.create();
        }

        @Override
        protected boolean compareLengths(int a, int b) {
            return a >= b;
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean contains(VirtualFrame frame, PArray self, Object value,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            for (int i = 0; i < self.getLength(); i++) {
                if (eqNode.execute(frame, getValueNode.execute(self, i), value)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, PArray self,
                        @Cached("create(Repr)") LookupAndCallUnaryNode reprNode,
                        @Cached ConditionProfile isEmptyProfile,
                        @Cached ConditionProfile isUnicodeProfile,
                        @Cached CastToTruffleStringNode cast,
                        @Cached ToUnicodeNode toUnicodeNode,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, T_ARRAY);
            appendStringNode.execute(sb, T_LPAREN);
            appendStringNode.execute(sb, T_SINGLE_QUOTE);
            appendStringNode.execute(sb, self.getFormatString());
            appendStringNode.execute(sb, T_SINGLE_QUOTE);
            if (isEmptyProfile.profile(self.getLength() != 0)) {
                if (isUnicodeProfile.profile(self.getFormat() == BufferFormat.UNICODE)) {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, cast.execute(reprNode.executeObject(frame, toUnicodeNode.execute(frame, self))));
                } else {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, T_LBRACKET);
                    for (int i = 0; i < self.getLength(); i++) {
                        if (i > 0) {
                            appendStringNode.execute(sb, T_COMMA_SPACE);
                        }
                        Object value = getValueNode.execute(self, i);
                        appendStringNode.execute(sb, cast.execute(reprNode.executeObject(frame, value)));
                    }
                    appendStringNode.execute(sb, T_RBRACKET);
                }
            }
            appendStringNode.execute(sb, T_RPAREN);
            return toStringNode.execute(sb);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idx)")
        static Object getitem(VirtualFrame frame, PArray self, Object idx,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached("forArray()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            int index = normalizeIndexNode.execute(indexNode.execute(frame, idx), self.getLength());
            return getValueNode.execute(self, index);
        }

        @Specialization
        Object getitem(PArray self, PSlice slice,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int itemsize = self.getFormat().bytesize;
            PArray newArray;
            try {
                newArray = factory().createArray(self.getFormatString(), self.getFormat(), sliceInfo.sliceLength);
            } catch (OverflowException e) {
                // It's a slice of existing array, the length cannot overflow
                throw CompilerDirectives.shouldNotReachHere();
            }

            if (simpleStepProfile.profile(sliceInfo.step == 1)) {
                PythonUtils.arraycopy(self.getBuffer(), sliceInfo.start * itemsize, newArray.getBuffer(), 0, sliceInfo.sliceLength * itemsize);
            } else {
                for (int i = sliceInfo.start, j = 0; j < sliceInfo.sliceLength; i += sliceInfo.step, j++) {
                    PythonUtils.arraycopy(self.getBuffer(), i * itemsize, newArray.getBuffer(), j * itemsize, itemsize);
                }
            }
            return newArray;
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!isPSlice(idx)")
        static Object setitem(VirtualFrame frame, PArray self, Object idx, Object value,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = normalizeIndexNode.execute(indexNode.execute(frame, idx), self.getLength());
            putValueNode.execute(frame, self, index, value);
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() == other.getFormat()")
        Object setitem(VirtualFrame frame, PArray self, PSlice slice, PArray other,
                        @Cached ConditionProfile sameArrayProfile,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached ConditionProfile complexDeleteProfile,
                        @Cached ConditionProfile differentLengthProfile,
                        @Cached ConditionProfile growProfile,
                        @Cached ConditionProfile stepAssignProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices,
                        @Cached DelItemNode delItemNode) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(self.getLength(), sliceUnpack.execute(slice));
            int start = sliceInfo.start;
            int stop = sliceInfo.stop;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            int itemsize = self.getFormat().bytesize;
            byte[] sourceBuffer = other.getBuffer();
            int needed = other.getLength();
            if (sameArrayProfile.profile(sourceBuffer == self.getBuffer())) {
                sourceBuffer = new byte[needed * itemsize];
                PythonUtils.arraycopy(other.getBuffer(), 0, sourceBuffer, 0, sourceBuffer.length);
            }
            if (simpleStepProfile.profile(step == 1)) {
                if (differentLengthProfile.profile(sliceLength != needed)) {
                    self.checkCanResize(this);
                    if (growProfile.profile(sliceLength < needed)) {
                        if (stop < start) {
                            stop = start;
                        }
                        try {
                            self.shift(stop, needed - sliceLength);
                        } catch (OverflowException e) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw raise(MemoryError);
                        }
                    } else {
                        self.delSlice(start, sliceLength - needed);
                    }
                }
                PythonUtils.arraycopy(sourceBuffer, 0, self.getBuffer(), start * itemsize, needed * itemsize);
            } else if (complexDeleteProfile.profile(needed == 0)) {
                delItemNode.executeSlice(frame, self, slice);
            } else if (stepAssignProfile.profile(needed == sliceLength)) {
                for (int cur = start, i = 0; i < sliceLength; cur += step, i++) {
                    PythonUtils.arraycopy(sourceBuffer, i * itemsize, self.getBuffer(), cur * itemsize, itemsize);
                }
            } else {
                throw raise(ValueError, ErrorMessages.ATTEMPT_ASSIGN_ARRAY_OF_SIZE, needed, sliceLength);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != other.getFormat()")
        @SuppressWarnings("unused")
        Object setitemWrongFormat(PArray self, PSlice slice, PArray other) {
            throw raise(TypeError, BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @Specialization(guards = "!isArray(other)")
        @SuppressWarnings("unused")
        Object setitemWrongType(PArray self, PSlice slice, Object other) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_ASSIGN_ARRAY, other);
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        public abstract Object executeSlice(VirtualFrame frame, PArray self, PSlice slice);

        @Specialization(guards = "!isPSlice(idx)")
        Object delitem(VirtualFrame frame, PArray self, Object idx,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached("forArrayAssign()") NormalizeIndexNode normalizeIndexNode) {
            self.checkCanResize(this);
            int index = normalizeIndexNode.execute(indexNode.execute(frame, idx), self.getLength());
            self.delSlice(index, 1);
            return PNone.NONE;
        }

        @Specialization
        Object delitem(PArray self, PSlice slice,
                        @Cached ConditionProfile simpleStepProfile,
                        @Cached SliceNodes.SliceUnpack sliceUnpack,
                        @Cached SliceNodes.AdjustIndices adjustIndices) {
            self.checkCanResize(this);
            int length = self.getLength();
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(length, sliceUnpack.execute(slice));
            int start = sliceInfo.start;
            int step = sliceInfo.step;
            int sliceLength = sliceInfo.sliceLength;
            int itemsize = self.getFormat().bytesize;
            if (sliceLength > 0) {
                if (simpleStepProfile.profile(step == 1)) {
                    self.delSlice(start, sliceLength);
                } else {
                    if (step < 0) {
                        start += 1 + step * (sliceLength - 1) - 1;
                        step = -step;
                    }
                    int cur, offset;
                    for (cur = start, offset = 0; offset < sliceLength - 1; cur += step, offset++) {
                        PythonUtils.arraycopy(self.getBuffer(), (cur + 1) * itemsize, self.getBuffer(), (cur - offset) * itemsize, (step - 1) * itemsize);
                    }
                    PythonUtils.arraycopy(self.getBuffer(), (cur + 1) * itemsize, self.getBuffer(), (cur - offset) * itemsize, (length - cur - 1) * itemsize);
                    self.setLength(length - sliceLength);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getitem(PArray self) {
            return factory().createArrayIterator(self);
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int len(PArray self) {
            return self.getLength();
        }
    }

    @Builtin(name = J___REDUCE_EX__, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "protocol"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class ReduceExNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ReduceExNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "protocol < 3")
        Object reduceLegacy(VirtualFrame frame, PArray self, @SuppressWarnings("unused") int protocol,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached ToListNode toListNode) {
            Object cls = getClassNode.execute(self);
            Object dict = lookup.execute(frame, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            PTuple args = factory().createTuple(new Object[]{self.getFormatString(), toListNode.execute(frame, self)});
            return factory().createTuple(new Object[]{cls, args, dict});
        }

        @Specialization(guards = "protocol >= 3")
        Object reduce(VirtualFrame frame, PArray self, @SuppressWarnings("unused") int protocol,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookupDict,
                        @Cached PyObjectGetAttr getReconstructor,
                        @Cached ToBytesNode toBytesNode) {
            PythonModule arrayModule = getCore().lookupBuiltinModule(T_ARRAY);
            PArray.MachineFormat mformat = PArray.MachineFormat.forFormat(self.getFormat());
            assert mformat != null;
            Object cls = getClassNode.execute(self);
            Object dict = lookupDict.execute(frame, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            Object reconstructor = getReconstructor.execute(frame, arrayModule, T_ARRAY_RECONSTRUCTOR);
            PTuple args = factory().createTuple(new Object[]{cls, self.getFormatString(), mformat.code, toBytesNode.execute(frame, self)});
            return factory().createTuple(new Object[]{reconstructor, args, dict});
        }
    }

    @Builtin(name = "itemsize", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ItemSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getItemSize(PArray self) {
            return self.getFormat().bytesize;
        }
    }

    @Builtin(name = "typecode", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TypeCodeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString getTypeCode(PArray self) {
            return self.getFormatString();
        }
    }

    @Builtin(name = "buffer_info", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BufferInfoNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object bufferinfo(PArray self) {
            // TODO return the C pointer
            return factory().createTuple(new Object[]{PythonAbstractObject.systemHashCode(self.getBuffer()), self.getLength()});
        }
    }

    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object append(VirtualFrame frame, PArray self, Object value,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            try {
                int index = self.getLength();
                int newLength = PythonUtils.addExact(index, 1);
                self.checkCanResize(this);
                self.resize(newLength);
                putValueNode.execute(frame, self, index, value);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }
    }

    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ExtendNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "self.getFormat() == value.getFormat()")
        Object extend(PArray self, PArray value) {
            try {
                int newLength = PythonUtils.addExact(self.getLength(), value.getLength());
                if (newLength != self.getLength()) {
                    self.checkCanResize(this);
                }
                int itemsize = self.getFormat().bytesize;
                self.resizeStorage(newLength);
                PythonUtils.arraycopy(value.getBuffer(), 0, self.getBuffer(), self.getLength() * itemsize, value.getLength() * itemsize);
                self.setLength(newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }

        @Specialization
        Object extend(VirtualFrame frame, PArray self, PSequence value,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(value);
            int storageLength = storage.length();
            try {
                int newLength = PythonUtils.addExact(self.getLength(), storageLength);
                if (newLength != self.getLength()) {
                    self.checkCanResize(this);
                }
                self.resizeStorage(newLength);
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
            int length = self.getLength();
            for (int i = 0; i < storageLength; i++) {
                // The whole extend is not atomic, just individual inserts are. That's the same as
                // in CPython
                putValueNode.execute(frame, self, length, getItemNode.execute(storage, i));
                self.setLength(++length);
            }

            return PNone.NONE;
        }

        @Specialization(guards = "!isArray(value)")
        Object extend(VirtualFrame frame, PArray self, Object value,
                        @Cached PyObjectGetIter getIter,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object iter = getIter.execute(frame, value);
            int length = self.getLength();
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                // The whole extend is not atomic, just individual inserts are. That's the same as
                // in CPython
                try {
                    length = PythonUtils.addExact(length, 1);
                    self.checkCanResize(this);
                    self.resizeStorage(length);
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }
                putValueNode.execute(frame, self, length - 1, nextValue);
                self.setLength(length);
            }

            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat() != value.getFormat()")
        @SuppressWarnings("unused")
        Object error(PArray self, PArray value) {
            // CPython allows extending an array with an arbitrary iterable. Except a differently
            // formatted array. Weird
            throw raise(TypeError, ErrorMessages.CAN_ONLY_EXTEND_WITH_ARRAY_OF_SAME_KIND);
        }
    }

    @Builtin(name = "insert", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "index", "value"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class InsertNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object insert(VirtualFrame frame, PArray self, int inputIndex, Object value,
                        @Cached("create(false)") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.CheckValueNode checkValueNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            int index = normalizeIndexNode.execute(inputIndex, self.getLength());
            if (index > self.getLength()) {
                index = self.getLength();
            } else if (index < 0) {
                index = 0;
            }
            // Need to check the validity of the value before moving the memory around to ensure the
            // operation can fail atomically
            checkValueNode.execute(frame, self, value);
            self.checkCanResize(this);
            try {
                self.shift(index, 1);
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
            putValueNode.execute(frame, self, index, value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.InsertNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object remove(VirtualFrame frame, PArray self, Object value,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            for (int i = 0; i < self.getLength(); i++) {
                Object item = getValueNode.execute(self, i);
                if (eqNode.execute(frame, item, value)) {
                    self.checkCanResize(this);
                    self.delSlice(i, 1);
                    return PNone.NONE;
                }
            }
            throw raise(ValueError, ErrorMessages.ARRAY_REMOVE_X_NOT_IN_ARRAY);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "index"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class PopNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object pop(PArray self, int inputIndex,
                        @Cached("forPop()") NormalizeIndexNode normalizeIndexNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            if (self.getLength() == 0) {
                throw raise(IndexError, ErrorMessages.POP_FROM_EMPTY_ARRAY);
            }
            int index = normalizeIndexNode.execute(inputIndex, self.getLength());
            Object value = getValueNode.execute(self, index);
            self.checkCanResize(this);
            self.delSlice(index, 1);
            return value;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.PopNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "frombytes", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer"})
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class FromBytesNode extends PythonBinaryClinicBuiltinNode {

        // make this method accessible
        @Override
        public abstract Object executeWithoutClinic(VirtualFrame frame, Object arg, Object arg2);

        @Specialization(limit = "3")
        Object frombytes(VirtualFrame frame, PArray self, Object buffer,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                int itemsize = self.getFormat().bytesize;
                int oldSize = self.getLength();
                try {
                    int bufferLength = bufferLib.getBufferLength(buffer);
                    if (bufferLength % itemsize != 0) {
                        throw raise(ValueError, ErrorMessages.BYTES_ARRAY_NOT_MULTIPLE_OF_ARRAY_SIZE);
                    }
                    int newLength = PythonUtils.addExact(oldSize, bufferLength / itemsize);
                    self.checkCanResize(this);
                    self.resize(newLength);
                    bufferLib.readIntoByteArray(buffer, 0, self.getBuffer(), oldSize * itemsize, bufferLength);
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw PRaiseNode.raiseUncached(this, MemoryError);
                }
                return PNone.NONE;
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.FromBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "fromfile", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "file", "n"})
    @ArgumentClinic(name = "n", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    public abstract static class FromFileNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object fromfile(VirtualFrame frame, PArray self, Object file, int n,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached ConditionProfile nNegativeProfile,
                        @Cached FromBytesNode fromBytesNode) {
            if (nNegativeProfile.profile(n < 0)) {
                throw raise(ValueError, ErrorMessages.NEGATIVE_COUNT);
            }
            int itemsize = self.getFormat().bytesize;
            int nbytes = n * itemsize;
            Object readResult = callMethod.execute(frame, file, T_READ, nbytes);
            if (readResult instanceof PBytes) {
                int readLength = sizeNode.execute(frame, readResult);
                fromBytesNode.executeWithoutClinic(frame, self, readResult);
                // It would make more sense to check this before the frombytes call, but CPython
                // does it this way
                if (readLength != nbytes) {
                    throw raise(EOFError, ErrorMessages.READ_DIDNT_RETURN_ENOUGH_BYTES);
                }
            } else {
                throw raise(TypeError, ErrorMessages.READ_DIDNT_RETURN_BYTES);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.FromFileNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "fromlist", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FromListNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object fromlist(VirtualFrame frame, PArray self, PList list,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            try {
                SequenceStorage storage = getSequenceStorageNode.execute(list);
                int length = storage.length();
                int newLength = PythonUtils.addExact(self.getLength(), length);
                self.checkCanResize(this);
                self.resizeStorage(newLength);
                for (int i = 0; i < length; i++) {
                    putValueNode.execute(frame, self, self.getLength() + i, getItemScalarNode.execute(storage, i));
                }
                self.setLength(newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.ARG_MUST_BE_LIST);
        }
    }

    @Builtin(name = "fromunicode", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "str"})
    @ArgumentClinic(name = "str", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromUnicodeNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object fromunicode(VirtualFrame frame, PArray self, TruffleString str,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            try {
                int length = codePointLengthNode.execute(str, TS_ENCODING);
                int newLength = PythonUtils.addExact(self.getLength(), length);
                self.checkCanResize(this);
                self.resizeStorage(newLength);
                TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
                int codePointIndex = 0;
                while (it.hasNext()) {
                    TruffleString value = fromCodePointNode.execute(nextNode.execute(it), TS_ENCODING, true);
                    putValueNode.execute(frame, self, self.getLength() + codePointIndex++, value);
                }
                self.setLength(newLength);
                return PNone.NONE;
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.FROMUNICODE_ARG_MUST_BE_STR_NOT_P, arg);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.FromUnicodeNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "tobytes", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ToBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object tobytes(PArray self) {
            byte[] bytes = new byte[self.getLength() * self.getFormat().bytesize];
            PythonUtils.arraycopy(self.getBuffer(), 0, bytes, 0, bytes.length);
            return factory().createBytes(bytes);
        }
    }

    @Builtin(name = "tolist", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ToListNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tolist(VirtualFrame frame, PArray self,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            return constructListNode.execute(frame, self);
        }
    }

    @Builtin(name = "tounicode", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ToUnicodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString tounicode(PArray self,
                        @Cached ConditionProfile formatProfile,
                        @Cached ArrayNodes.GetValueNode getValueNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            if (formatProfile.profile(self.getFormat() != BufferFormat.UNICODE)) {
                throw raise(ValueError, ErrorMessages.MAY_ONLY_BE_CALLED_ON_UNICODE_TYPE_ARRAYS);
            }
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            for (int i = 0; i < self.getLength(); i++) {
                appendStringNode.execute(sb, (TruffleString) getValueNode.execute(self, i));
            }
            return toStringNode.execute(sb);
        }
    }

    @Builtin(name = "tofile", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ToFileNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object tofile(VirtualFrame frame, PArray self, Object file,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            if (self.getLength() > 0) {
                int remaining = self.getLength() * self.getFormat().bytesize;
                int blocksize = 64 * 1024;
                int nblocks = (remaining + blocksize - 1) / blocksize;
                byte[] buffer = null;
                for (int i = 0; i < nblocks; i++) {
                    if (remaining < blocksize) {
                        buffer = new byte[remaining];
                    } else if (buffer == null) {
                        buffer = new byte[blocksize];
                    }
                    PythonUtils.arraycopy(self.getBuffer(), i * blocksize, buffer, 0, buffer.length);
                    callMethod.execute(frame, file, T_WRITE, factory().createBytes(buffer));
                    remaining -= blocksize;
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "byteswap", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteSwapNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.getFormat().bytesize == 1")
        static Object byteswap1(@SuppressWarnings("unused") PArray self) {
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat().bytesize == 2")
        static Object byteswap2(PArray self) {
            doByteSwapExploded(self, 2, self.getBuffer());
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat().bytesize == 4")
        static Object byteswap4(PArray self) {
            doByteSwapExploded(self, 4, self.getBuffer());
            return PNone.NONE;
        }

        @Specialization(guards = "self.getFormat().bytesize == 8")
        static Object byteswap8(PArray self) {
            doByteSwapExploded(self, 8, self.getBuffer());
            return PNone.NONE;
        }

        private static void doByteSwapExploded(PArray self, int itemsize, byte[] buffer) {
            for (int i = 0; i < self.getLength() * itemsize; i += itemsize) {
                doByteSwapExplodedInnerLoop(buffer, itemsize, i);
            }
        }

        @ExplodeLoop
        private static void doByteSwapExplodedInnerLoop(byte[] buffer, int itemsize, int i) {
            for (int j = 0; j < itemsize / 2; j++) {
                byte b = buffer[i + j];
                buffer[i + j] = buffer[i + itemsize - j - 1];
                buffer[i + itemsize - j - 1] = b;
            }
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization
        int index(VirtualFrame frame, PArray self, Object value, int start, int stop,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            if (start < 0) {
                start += self.getLength();
                if (start < 0) {
                    start = 0;
                }
            }
            if (stop < 0) {
                stop += self.getLength();
            }
            for (int i = start; i < stop && i < self.getLength(); i++) {
                if (eqNode.execute(frame, getValueNode.execute(self, i), value)) {
                    return i;
                }
            }
            throw raise(ValueError, ErrorMessages.ARRAY_INDEX_X_NOT_IN_ARRAY);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayBuiltinsClinicProviders.IndexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonBinaryBuiltinNode {
        @Specialization
        static int count(VirtualFrame frame, PArray self, Object value,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ArrayNodes.GetValueNode getValueNode) {
            int count = 0;
            for (int i = 0; i < self.getLength(); i++) {
                if (eqNode.execute(frame, getValueNode.execute(self, i), value)) {
                    count++;
                }
            }
            return count;
        }
    }

    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReverseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reverse(PArray self) {
            int itemsize = self.getFormat().bytesize;
            byte[] tmp = new byte[itemsize];
            int length = self.getLength();
            byte[] buffer = self.getBuffer();
            for (int i = 0; i < length / 2; i++) {
                PythonUtils.arraycopy(buffer, i * itemsize, tmp, 0, itemsize);
                PythonUtils.arraycopy(buffer, (length - i - 1) * itemsize, buffer, i * itemsize, itemsize);
                PythonUtils.arraycopy(tmp, 0, buffer, (length - i - 1) * itemsize, itemsize);
            }
            return PNone.NONE;
        }
    }
}
