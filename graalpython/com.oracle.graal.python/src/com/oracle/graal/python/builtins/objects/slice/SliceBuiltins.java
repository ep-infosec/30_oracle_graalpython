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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.SliceCastToToBigInt;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.SliceExactCastToInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSlice)
public class SliceBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SliceBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static TruffleString repr(PSlice self) {
            return toTruffleStringUncached(self.toString());
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        static boolean sliceCmp(PIntSlice left, PIntSlice right) {
            return left.equals(right);
        }

        /**
         * As per {@link "https://github.com/python/cpython/blob/master/Objects/sliceobject.c#L569"}
         *
         *
         * both {@code left} and {@code right} must be a slice.
         *
         * @return CPython returns {@code NOTIMPLEMENTED} which will eventually yield {@value false}
         *         , so we shortcut and return {@value false}.
         */
        @SuppressWarnings("unused")
        @Specialization(guards = "!isPSlice(right)")
        static boolean notEqual(PSlice left, Object right) {
            return false;
        }

        @Specialization
        static boolean sliceCmpWithLib(VirtualFrame frame, PSlice left, PSlice right,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            return eqNode.execute(frame, left.getStart(), right.getStart()) &&
                            eqNode.execute(frame, left.getStop(), right.getStop()) &&
                            eqNode.execute(frame, left.getStep(), right.getStep());
        }

    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StartNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected static Object get(PIntSlice self) {
            return self.getStart();
        }

        @Specialization
        protected static Object get(PObjectSlice self) {
            return self.getStart();
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StopNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected static Object get(PIntSlice self) {
            return self.getStop();
        }

        @Specialization
        protected static Object get(PObjectSlice self) {
            return self.getStop();
        }
    }

    @Builtin(name = "step", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StepNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected static Object get(PIntSlice self) {
            return self.getStep();
        }

        @Specialization
        protected static Object get(PObjectSlice self) {
            return self.getStep();
        }
    }

    @Builtin(name = "indices", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IndicesNode extends PythonBinaryBuiltinNode {

        private PTuple doPSlice(VirtualFrame frame, PSlice self, int length, ComputeIndices compute) {
            SliceInfo sliceInfo = compute.execute(frame, self, length);
            return factory().createTuple(new Object[]{sliceInfo.start, sliceInfo.stop, sliceInfo.step});
        }

        protected static boolean isSafeIntSlice(PSlice self, Object length) {
            return self instanceof PIntSlice && length instanceof Integer;
        }

        @Specialization
        protected PTuple safeInt(VirtualFrame frame, PIntSlice self, int length,
                        @Cached ComputeIndices compute) {
            return doPSlice(frame, self, length, compute);
        }

        @Specialization(guards = "!isPNone(length)", rewriteOn = PException.class)
        protected PTuple doSliceObject(VirtualFrame frame, PSlice self, Object length,
                        @Cached SliceExactCastToInt toInt,
                        @Cached ComputeIndices compute) {
            return doPSlice(frame, self, (int) toInt.execute(frame, length), compute);
        }

        @Specialization(guards = "!isPNone(length)", replaces = {"doSliceObject"})
        protected PTuple doSliceObjectWithSlowPath(VirtualFrame frame, PSlice self, Object length,
                        @Cached SliceExactCastToInt toInt,
                        @Cached ComputeIndices compute,
                        @Cached IsBuiltinClassProfile profileError,
                        @Cached SliceCastToToBigInt castLengthNode,
                        @Cached CoerceToObjectSlice castNode) {
            try {
                return doPSlice(frame, self, (int) toInt.execute(frame, length), compute);
            } catch (PException pe) {
                if (!profileError.profileException(pe, PythonBuiltinClassType.OverflowError)) {
                    throw pe;
                }
                // pass
            }

            Object lengthIn = castLengthNode.execute(length);
            PObjectSlice.SliceObjectInfo sliceInfo = PObjectSlice.computeIndicesSlowPath(castNode.execute(self), lengthIn, factory());
            return factory().createTuple(new Object[]{sliceInfo.start, sliceInfo.stop, sliceInfo.step});
        }

        @Specialization(guards = {"isPNone(length)"})
        protected static PTuple lengthNone(@SuppressWarnings("unused") PSlice self, @SuppressWarnings("unused") Object length,
                        @Cached PRaiseNode raise) {
            throw raise.raise(ValueError);
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HashNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public static long hash(PSlice self,
                        @Cached PRaiseNode raise) {
            CompilerDirectives.transferToInterpreter();
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.UNHASHABLE_TYPE_P, PythonBuiltinClassType.PSlice);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PSlice self,
                        @Cached GetClassNode getClassNode) {
            PTuple args = factory().createTuple(new Object[]{self.getStart(), self.getStop(), self.getStep()});
            return factory().createTuple(new Object[]{getClassNode.execute(self), args});
        }
    }
}
