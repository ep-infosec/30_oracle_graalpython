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
package com.oracle.graal.python.builtins.objects.range;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.CoerceToBigRange;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfIntRangeNodeExact;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.PRangeStartNode;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.PRangeStepNode;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.PRangeStopNode;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice.SliceObjectInfo;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRange)
public class RangeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RangeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @Specialization
        public long hash(VirtualFrame frame, PIntRange self,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode) {
            Object[] content = new Object[3];
            int intLength = self.getIntLength();
            content[0] = intLength;
            if (intLength == 0) {
                content[1] = PNone.NONE;
                content[2] = PNone.NONE;
            } else if (intLength == 1) {
                content[1] = self.getIntStart();
                content[2] = PNone.NONE;
            } else {
                content[1] = self.getIntStart();
                content[2] = self.getIntStep();
            }
            return hashNode.execute(frame, factory().createTuple(content));
        }

        @Specialization
        public long hash(VirtualFrame frame, PBigRange self,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode) {
            Object[] content = new Object[3];
            PInt length = self.getPIntLength();
            content[0] = length;
            if (length.compareTo(BigInteger.ZERO) == 0) {
                content[1] = PNone.NONE;
                content[2] = PNone.NONE;
            } else if (length.compareTo(BigInteger.ONE) == 0) {
                content[1] = self.getStart();
                content[2] = PNone.NONE;
            } else {
                content[1] = self.getStart();
                content[2] = self.getStep();
            }
            return hashNode.execute(frame, factory().createTuple(content));
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        public static TruffleString repr(PRange self,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString start = repr.execute(null, self.getStart());
            TruffleString stop = repr.execute(null, self.getStop());
            if (self.withStep()) {
                return simpleTruffleStringFormatNode.format("range(%s, %s, %s)", start, stop, repr.execute(null, self.getStep()));
            } else {
                return simpleTruffleStringFormatNode.format("range(%s, %s)", start, stop);
            }
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int doPIntRange(PIntRange self) {
            return self.getIntLength();
        }

        @Specialization
        int doPBigRange(VirtualFrame frame, PBigRange self,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            Object length = self.getLength();
            if (indexCheckNode.execute(length)) {
                return asSizeNode.executeExact(frame, length);
            }
            throw raise(OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, length);
        }
    }

    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doPIntRange(PIntRange self) {
            return self.getIntLength() != 0;
        }

        @Specialization
        @TruffleBoundary
        boolean doPBigRange(PBigRange self) {
            return self.getBigIntegerLength().compareTo(BigInteger.ZERO) != 0;
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doPIntRange(PIntRange self) {
            return factory().createIntRangeIterator(self);
        }

        @Specialization
        Object doPIntRange(PBigRange self) {
            return factory().createBigRangeIterator(self);
        }
    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StartNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object start(PRange self,
                        @Cached PRangeStartNode get) {
            return get.execute(self);
        }
    }

    @Builtin(name = "step", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StepNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object step(PRange self,
                        @Cached PRangeStepNode get) {
            return get.execute(self);
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StopNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object stop(PRange self,
                        @Cached PRangeStopNode get) {
            return get.execute(self);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PRange self,
                        @Cached GetClassNode getClassNode) {
            PTuple args = factory().createTuple(new Object[]{self.getStart(), self.getStop(), self.getStep()});
            return factory().createTuple(new Object[]{getClassNode.execute(self), args});
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        private static boolean eqInt(PIntRange range, int len, int start, int step) {
            return eqInt(range.getIntLength(), range.getIntStart(), range.getIntStep(),
                            len, start, step);
        }

        private static boolean eqInt(int llen, int lstart, int lstep, int rlen, int rstart, int rstep) {
            if (llen != rlen) {
                return false;
            }
            if (llen == 0) {
                return true;
            }
            if (lstart != rstart) {
                return false;
            }
            // same start, just one element => step does not matter
            if (llen == 1) {
                return true;
            }
            return lstep == rstep;
        }

        @TruffleBoundary
        private static boolean eqBigInt(BigInteger llen, BigInteger lstart, BigInteger lstep, BigInteger rlen, BigInteger rstart, BigInteger rstep) {
            if (llen.compareTo(rlen) != 0) {
                return false;
            }
            if (llen.compareTo(BigInteger.ZERO) == 0) {
                return true;
            }
            if (lstart.compareTo(rstart) != 0) {
                return false;
            }
            // same start, just one element => step does not matter
            if (llen.compareTo(BigInteger.ONE) == 0) {
                return true;
            }
            return lstep.compareTo(rstep) == 0;
        }

        @Specialization
        boolean eqIntInt(PIntRange left, PIntRange right) {
            if (left == right) {
                return true;
            }
            return eqInt(left.getIntLength(), left.getIntStart(), left.getIntStep(),
                            right.getIntLength(), right.getIntStart(), right.getIntStep());
        }

        @Specialization
        boolean eqIntBig(VirtualFrame frame, PIntRange left, PBigRange right,
                        @Cached RangeNodes.CoerceToBigRange leftToBigRange,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            try {
                int rlen = asSizeNode.executeExact(frame, right.getPIntLength());
                int rstart = asSizeNode.executeExact(frame, right.getPIntStart());
                int rstep = asSizeNode.executeExact(frame, right.getPIntStep());
                return eqInt(left, rlen, rstart, rstep);
            } catch (PException e) {
                return eqBigInt(leftToBigRange.execute(left, factory()), right);
            }
        }

        @Specialization
        boolean eqIntBig(VirtualFrame frame, PBigRange left, PIntRange right,
                        @Cached RangeNodes.CoerceToBigRange rightToBigRange,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            try {
                int llen = asSizeNode.executeExact(frame, left.getPIntLength());
                int lstart = asSizeNode.executeExact(frame, left.getPIntStart());
                int lstep = asSizeNode.executeExact(frame, left.getPIntStep());
                return eqInt(right, llen, lstart, lstep);
            } catch (PException e) {
                return eqBigInt(left, rightToBigRange.execute(right, factory()));
            }
        }

        @Specialization
        boolean eqBigInt(PBigRange left, PBigRange right) {
            if (left == right) {
                return true;
            }
            return eqBigInt(left.getBigIntegerLength(), left.getBigIntegerStart(), left.getBigIntegerStep(),
                            right.getBigIntegerLength(), right.getBigIntegerStart(), right.getBigIntegerStep());
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doOther(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.forRange();

        protected static boolean allNone(PObjectSlice slice) {
            return slice.getStart() == PNone.NONE && slice.getStop() == PNone.NONE && slice.getStep() == PNone.NONE;
        }

        protected static boolean canBeIndex(Object idx, PyIndexCheckNode indexCheckNode) {
            return indexCheckNode.execute(idx);
        }

        @Specialization(guards = "allNone(slice)")
        Object doPRangeObj(PRange self, @SuppressWarnings("unused") PObjectSlice slice) {
            return self;
        }

        @Specialization(guards = "canBeIndex(idx, indexCheckNode)")
        Object doPRange(VirtualFrame frame, PIntRange self, Object idx,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            return self.getIntItemNormalized(normalize.execute(asSizeNode.executeExact(frame, idx), self.getIntLength()));
        }

        @Specialization(guards = "canBeIndex(idx, indexCheckNode)")
        Object doPRange(PBigRange self, Object idx,
                        @Cached CastToJavaBigIntegerNode toBigInt,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode) {
            return factory().createInt(self.getBigIntItemNormalized(computeBigRangeItem(self, idx, toBigInt)));
        }

        @Specialization(guards = "!canBeIndex(slice, indexCheckNode)")
        Object doPRangeSliceSlowPath(VirtualFrame frame, PIntRange self, PSlice slice,
                        @Cached ComputeIndices compute,
                        @Cached IsBuiltinClassProfile profileError,
                        @Cached CoerceToBigRange toBigIntRange,
                        @Cached CoerceToObjectSlice toBigIntSlice,
                        @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode) {
            try {
                final int rStart = self.getIntStart();
                final int rStep = self.getIntStep();
                SliceInfo info = compute.execute(frame, slice, self.getIntLength());
                return createRange(info, rStart, rStep, lenOfRangeNodeExact);
            } catch (PException pe) {
                pe.expect(PythonBuiltinClassType.OverflowError, profileError);
                // pass
            } catch (CannotCastException | OverflowException e) {
                // pass
            }
            PBigRange rangeBI = toBigIntRange.execute(self, factory());
            BigInteger rangeStart = rangeBI.getBigIntegerStart();
            BigInteger rangeStep = rangeBI.getBigIntegerStep();

            SliceObjectInfo info = PObjectSlice.computeIndicesSlowPath(toBigIntSlice.execute(slice), rangeBI.getBigIntegerLength(), null);
            return createRange(info, rangeStart, rangeStep, lenOfRangeNode);
        }

        @Specialization(guards = "!canBeIndex(slice, indexCheckNode)")
        Object doPRangeSliceSlowPath(VirtualFrame frame, PBigRange self, PSlice slice,
                        @Cached ComputeIndices compute,
                        @Cached IsBuiltinClassProfile profileError,
                        @Cached CoerceToBigRange toBigIntRange,
                        @Cached CoerceToObjectSlice toBigIntSlice,
                        @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            try {
                int rStart = asSizeNode.executeExact(frame, self.getStart());
                int rStep = asSizeNode.executeExact(frame, self.getStep());
                SliceInfo info = compute.execute(frame, slice, asSizeNode.executeExact(frame, self.getLength()));
                return createRange(info, rStart, rStep, lenOfRangeNodeExact);
            } catch (PException pe) {
                pe.expect(PythonBuiltinClassType.OverflowError, profileError);
                // pass
            } catch (CannotCastException | OverflowException e) {
                // pass
            }
            PBigRange rangeBI = toBigIntRange.execute(self, factory());
            BigInteger rangeStart = rangeBI.getBigIntegerStart();
            BigInteger rangeStep = rangeBI.getBigIntegerStep();

            SliceObjectInfo info = PObjectSlice.computeIndicesSlowPath(toBigIntSlice.execute(slice), rangeBI.getBigIntegerLength(), null);
            return createRange(info, rangeStart, rangeStep, lenOfRangeNode);
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, PRange self, Object idx,
                        @Cached ConditionProfile isNumIndexProfile,
                        @Cached ConditionProfile isSliceIndexProfile,
                        @Cached ComputeIndices compute,
                        @Cached IsBuiltinClassProfile profileError,
                        @Cached CoerceToBigRange toBigIntRange,
                        @Cached CoerceToObjectSlice toBigIntSlice,
                        @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Cached CastToJavaBigIntegerNode toBigInt,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (isNumIndexProfile.profile(canBeIndex(idx, indexCheckNode))) {
                if (self instanceof PIntRange) {
                    return doPRange(frame, (PIntRange) self, idx, indexCheckNode, asSizeNode);
                }
                return doPRange((PBigRange) self, idx, toBigInt, indexCheckNode);
            }
            if (isSliceIndexProfile.profile(idx instanceof PSlice)) {
                PSlice slice = (PSlice) idx;
                if (self instanceof PIntRange) {
                    return doPRangeSliceSlowPath(frame, (PIntRange) self, slice, compute, profileError, toBigIntRange, toBigIntSlice, lenOfRangeNodeExact, lenOfRangeNode, indexCheckNode);
                }
                return doPRangeSliceSlowPath(frame, (PBigRange) self, slice, compute, profileError, toBigIntRange, toBigIntSlice, lenOfRangeNodeExact, lenOfRangeNode, indexCheckNode, asSizeNode);
            }
            throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "range", idx);
        }

        @TruffleBoundary
        private BigInteger computeBigRangeItem(PBigRange range, Object idx, CastToJavaBigIntegerNode toBigInt) {
            BigInteger index = toBigInt.execute(idx);
            BigInteger length = range.getBigIntegerLength();
            BigInteger i;
            if (index.compareTo(BigInteger.ZERO) < 0) {
                i = length.add(index);
            } else {
                i = index;
            }

            if (i.compareTo(BigInteger.ZERO) < 0 || i.compareTo(length) >= 0) {
                throw raise(IndexError, ErrorMessages.RANGE_OBJ_IDX_OUT_OF_RANGE);
            }
            return i;
        }

        private PIntRange createRange(SliceInfo info, int rStart, int rStep, LenOfIntRangeNodeExact lenOfRangeNode) throws OverflowException {
            int newStep = rStep * info.step;
            int newStart = rStart + info.start * rStep;
            int newStop = rStart + info.stop * rStep;
            int len = lenOfRangeNode.executeInt(newStart, newStop, newStep);
            return factory().createIntRange(newStart, newStop, newStep, len);
        }

        @TruffleBoundary
        private PBigRange createRange(SliceObjectInfo info, BigInteger rStart, BigInteger rStep, RangeNodes.LenOfRangeNode lenOfRangeNode) {
            BigInteger sliceStart = (BigInteger) info.start;
            BigInteger sliceStop = (BigInteger) info.stop;
            BigInteger sliceStep = (BigInteger) info.step;

            BigInteger step = rStep.multiply(sliceStep);
            BigInteger start = rStart.add(sliceStart.multiply(rStep));
            BigInteger stop = rStart.add(sliceStop.multiply(rStep));
            BigInteger len = lenOfRangeNode.execute(start, stop, step);
            return factory().createBigRange(factory().createInt(start), factory().createInt(stop), factory().createInt(step), factory().createInt(len));
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        private final ConditionProfile stepOneProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile stepMinusOneProfile = ConditionProfile.createBinaryProfile();
        private static final BigInteger MINUS_ONE = BigInteger.ONE.negate();

        public abstract boolean execute(VirtualFrame frame, PRange self, Object value);

        private boolean containsInt(PIntRange self, int other) {
            int step = self.getIntStep();
            int start = self.getIntStart();
            int stop = self.getIntStop();
            boolean cmp2;
            boolean cmp3;

            if (stepOneProfile.profile(step == 1)) {
                return other >= start && other < stop;
            } else if (stepMinusOneProfile.profile(step == -1)) {
                return other <= start && other > stop;
            } else {
                assert step != 0;
                if (step > 0) {
                    // positive steps: start <= ob < stop
                    cmp2 = start <= other;
                    cmp3 = other < stop;
                } else {
                    // negative steps: stop < ob <= start
                    cmp2 = stop < other;
                    cmp3 = other <= start;
                }

                if (!cmp2 || !cmp3) {
                    return false;
                }

                // Check that the stride does not invalidate ob's membership.
                return (other - start) % step == 0;
            }
        }

        @TruffleBoundary
        private boolean containsBigInt(PBigRange self, long other) {
            return containsBigInt(self, BigInteger.valueOf(other));
        }

        @TruffleBoundary
        private boolean containsBigInt(PBigRange self, BigInteger other) {
            BigInteger step = self.getBigIntegerStep();
            BigInteger start = self.getBigIntegerStart();
            BigInteger stop = self.getBigIntegerStop();
            boolean cmp2;
            boolean cmp3;

            if (stepOneProfile.profile(step.compareTo(BigInteger.ONE) == 0)) {
                return other.compareTo(start) >= 0 && other.compareTo(stop) < 0;
            } else if (stepMinusOneProfile.profile(step.compareTo(MINUS_ONE) == 0)) {
                return other.compareTo(start) <= 0 && other.compareTo(stop) > 0;
            } else {
                assert step.compareTo(BigInteger.ZERO) != 0;
                if (step.compareTo(BigInteger.ZERO) > 0) {
                    // positive steps: start <= ob < stop
                    cmp2 = start.compareTo(other) <= 0;
                    cmp3 = other.compareTo(stop) < 0;
                } else {
                    // negative steps: stop < ob <= start
                    cmp2 = stop.compareTo(other) < 0;
                    cmp3 = other.compareTo(start) <= 0;
                }

                if (!cmp2 || !cmp3) {
                    return false;
                }

                // Check that the stride does not invalidate ob's membership.
                BigInteger tmp1 = other.subtract(start);
                if (tmp1.compareTo(BigInteger.ZERO) == 0) {
                    return true;
                }
                return tmp1.mod(step).compareTo(BigInteger.ZERO) == 0;
            }
        }

        protected boolean doubleIsExactInteger(double value) {
            return (value % 1) == 0;
        }

        @Specialization
        boolean containsFastNumInt(PIntRange self, int other) {
            return containsInt(self, other);
        }

        @Specialization
        boolean containsFastNumLong(PIntRange self, long other) {
            try {
                return containsInt(self, PInt.intValueExact(other));
            } catch (OverflowException e) {
                return false;
            }
        }

        protected static boolean isBuiltinPInt(Object value, PyLongCheckExactNode isBuiltin) {
            return isBuiltin.execute(value);
        }

        @Specialization(guards = "isBuiltinPInt(other, isBuiltin)", limit = "1")
        boolean containsFastNumPInt(PIntRange self, PInt other,
                        @SuppressWarnings("unused") @Shared("isBuiltinPInt") @Cached PyLongCheckExactNode isBuiltin) {
            try {
                return containsInt(self, other.intValueExact());
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = "doubleIsExactInteger(other)")
        boolean containsFastNum(PIntRange self, double other) {
            return containsInt(self, (int) other);
        }

        @Specialization
        boolean containsSlowNum(PBigRange self, int other) {
            return containsBigInt(self, other);
        }

        @Specialization
        boolean containsSlowNum(PBigRange self, long other) {
            return containsBigInt(self, other);
        }

        @Specialization(guards = "doubleIsExactInteger(other)")
        boolean containsSlowNum(PBigRange self, double other) {
            return containsBigInt(self, (long) other);
        }

        @Specialization(guards = "isBuiltinPInt(other, isBuiltin)", limit = "1")
        boolean containsSlowNum(PBigRange self, PInt other,
                        @SuppressWarnings("unused") @Shared("isBuiltinPInt") @Cached PyLongCheckExactNode isBuiltin) {
            return containsBigInt(self, other.getValue());
        }

        @Specialization(guards = "!canBeInteger(elem) || !isBuiltinPInt(elem, isBuiltin)", limit = "1")
        static boolean containsIterator(VirtualFrame frame, PRange self, Object elem,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @SuppressWarnings("unused") @Shared("isBuiltinPInt") @Cached PyLongCheckExactNode isBuiltin) {
            Object iter = getIter.execute(frame, self);
            while (true) {
                try {
                    Object item = nextNode.execute(frame, iter);
                    if (eqNode.execute(frame, elem, item)) {
                        return true;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return false;
        }

        public static ContainsNode create() {
            return RangeBuiltinsFactory.ContainsNodeFactory.create();
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class IndexNode extends PythonBinaryBuiltinNode {

        private static int fastIntIndex(PIntRange self, int elem) {
            int normalized = elem - self.getIntStart();
            if (normalized % self.getIntStep() == 0) {
                return normalized / self.getIntStep();
            }
            return -1;
        }

        @TruffleBoundary
        private static BigInteger slowIntIndex(PBigRange self, Object elem, CastToJavaBigIntegerNode castToBigInt) {
            BigInteger start = self.getBigIntegerStart();
            BigInteger step = self.getBigIntegerStep();
            BigInteger value = castToBigInt.execute(elem);
            BigInteger normalized = value.subtract(start);
            if (normalized.remainder(step).equals(BigInteger.ZERO)) {
                return normalized.divide(step);
            }
            return null;
        }

        @Specialization
        int doFastRange(VirtualFrame frame, PIntRange self, int elem,
                        @Cached ContainsNode containsNode) {
            if (containsNode.execute(frame, self, elem)) {
                int index = fastIntIndex(self, elem);
                if (index != -1) {
                    return index;
                }
            }
            throw raise(ValueError, ErrorMessages.D_IS_NOT_IN_RANGE, elem);
        }

        @Specialization(guards = "canBeInteger(elem)")
        Object doFastRangeGeneric(VirtualFrame frame, PIntRange self, Object elem,
                        @Cached ContainsNode containsNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (containsNode.execute(frame, self, elem)) {
                int value = asSizeNode.executeExact(frame, elem);
                int index = fastIntIndex(self, value);
                if (index != -1) {
                    return index;
                }
            }
            throw raise(ValueError, ErrorMessages.IS_NOT_IN_RANGE, elem);
        }

        @Specialization(guards = "canBeInteger(elem)")
        Object doLongRange(VirtualFrame frame, PBigRange self, Object elem,
                        @Cached ContainsNode containsNode,
                        @Cached CastToJavaBigIntegerNode castToBigInt) {
            if (containsNode.execute(frame, self, elem)) {
                BigInteger index = slowIntIndex(self, elem, castToBigInt);
                if (index != null) {
                    return factory().createInt(index);
                }
            }
            throw raise(ValueError, ErrorMessages.D_IS_NOT_IN_RANGE, elem);
        }

        /**
         * XXX: (mq) currently sys.maxsize in {@link SysModuleBuiltins#MAXSIZE} is
         * {@link Integer#MAX_VALUE}.
         */
        @Specialization(guards = "!canBeInteger(elem)")
        Object containsIterator(VirtualFrame frame, PIntRange self, Object elem,
                        @Cached GetNextNode nextNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            int idx = 0;
            Object iter = getIter.execute(frame, self);
            while (true) {
                try {
                    Object item = nextNode.execute(frame, iter);
                    if (eqNode.execute(frame, elem, item)) {
                        return idx;
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                if (idx == SysModuleBuiltins.MAXSIZE) {
                    throw raiseOverflow();
                }
                idx++;
            }
            throw raise(ValueError, ErrorMessages.D_IS_NOT_IN_RANGE, elem);
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doInt(PIntRange self, int elem) {
            assert self.getIntStep() != 0;
            if (elem >= self.getIntStart() && elem < self.getIntStop()) {
                int normalized = elem - self.getIntStart();
                if (normalized % self.getIntStep() == 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization
        int doInt(PIntRange self, long elem) {
            assert self.getIntStep() != 0;
            if (elem >= self.getIntStart() && elem < self.getIntStop()) {
                long normalized = elem - self.getIntStart();
                if (normalized % self.getIntStep() == 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization
        @TruffleBoundary
        int doInt(PIntRange self, PInt elem) {
            assert self.getIntStep() != 0;
            BigInteger value = elem.getValue();
            BigInteger start = BigInteger.valueOf(self.getIntStart());
            BigInteger stop = BigInteger.valueOf(self.getIntStop());
            BigInteger step = BigInteger.valueOf(self.getIntStep());
            if (value.compareTo(start) >= 0 && value.compareTo(stop) < 0) {
                BigInteger normalized = value.subtract(start);
                if (normalized.remainder(step).equals(BigInteger.ZERO)) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = "isInteger(elem)")
        @TruffleBoundary
        int doInt(PBigRange self, Object elem,
                        @Cached CastToJavaBigIntegerNode castToBigInt) {
            BigInteger start = self.getBigIntegerStart();
            BigInteger stop = self.getBigIntegerStop();
            BigInteger step = self.getBigIntegerStep();
            BigInteger value = castToBigInt.execute(elem);
            if (value.compareTo(start) >= 0 && value.compareTo(stop) < 0) {
                BigInteger normalized = value.subtract(start);
                if (normalized.remainder(step).equals(BigInteger.ZERO)) {
                    return 1;
                }
            }
            return 0;
        }

        static boolean isInteger(Object value) {
            return value instanceof Integer || value instanceof Long || value instanceof PInt;
        }

        static boolean isFallback(Object value) {
            return !isInteger(value);
        }

        private int incCount(int count) {
            if (count == SysModuleBuiltins.MAXSIZE) {
                throw raiseOverflow();
            }
            return count + 1;
        }

        @Specialization(guards = "isFallback(elem)")
        int doGeneric(VirtualFrame frame, PRange self, Object elem,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            int count = 0;
            Object iter = getIter.execute(frame, self);
            while (true) {
                try {
                    Object item = nextNode.execute(frame, iter);
                    if (eqNode.execute(frame, elem, item)) {
                        count = incCount(count);
                    }
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return count;
        }
    }
}
