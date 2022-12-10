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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

public abstract class SliceNodes {
    @GenerateUncached
    public abstract static class CreateSliceNode extends PNodeWithContext {
        public abstract PSlice execute(Object start, Object stop, Object step);

        @SuppressWarnings("unused")
        static PSlice doInt(int start, int stop, PNone step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(start, stop, 1, false, true);
        }

        @Specialization
        static PSlice doInt(int start, int stop, int step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(start, stop, step);
        }

        @Specialization
        @SuppressWarnings("unused")
        static PSlice doInt(PNone start, int stop, PNone step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(0, stop, 1, true, true);
        }

        @Specialization
        @SuppressWarnings("unused")
        static PSlice doInt(PNone start, int stop, int step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createIntSlice(0, stop, step, true, false);
        }

        @Fallback
        static PSlice doGeneric(Object start, Object stop, Object step,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start, stop, step);
        }

        public static CreateSliceNode create() {
            return SliceNodesFactory.CreateSliceNodeGen.create();
        }

        public static CreateSliceNode getUncached() {
            return SliceNodesFactory.CreateSliceNodeGen.getUncached();
        }
    }

    /**
     * Adopting logic from PySlice_AdjustIndices (sliceobject.c:248)
     */
    @GenerateUncached
    public abstract static class AdjustIndices extends PNodeWithContext {

        public abstract PSlice.SliceInfo execute(int length, PSlice.SliceInfo slice);

        @Specialization
        PSlice.SliceInfo calc(int length, PSlice.SliceInfo slice,
                        @Cached PRaiseNode raiseNode) {
            int start = slice.start;
            int stop = slice.stop;
            int step = slice.step;

            if (step == 0) {
                raiseNode.raise(ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
            }
            assert step > Integer.MIN_VALUE : "step must not be minimum integer value";

            int len = 0;
            if (start < 0) {
                start += length;
                if (start < 0) {
                    start = (step < 0) ? -1 : 0;
                }
            } else if (start >= length) {
                start = (step < 0) ? length - 1 : length;
            }

            if (stop < 0) {
                stop += length;
                if (stop < 0) {
                    stop = (step < 0) ? -1 : 0;
                }
            } else if (stop >= length) {
                stop = (step < 0) ? length - 1 : length;
            }

            if (step < 0) {
                if (stop < start) {
                    len = (start - stop - 1) / (-step) + 1;
                }
            } else {
                if (start < stop) {
                    len = (stop - start - 1) / step + 1;
                }
            }
            return new PSlice.SliceInfo(start, stop, step, len);
        }
    }

    /**
     * Coerce indices computation to lossy integer values
     */
    @GenerateUncached
    public abstract static class ComputeIndices extends PNodeWithContext {

        public abstract PSlice.SliceInfo execute(Frame frame, PSlice slice, int i);

        @Specialization(guards = "length >= 0")
        PSlice.SliceInfo doSliceInt(PIntSlice slice, int length) {
            return slice.computeIndices(length);
        }

        @Specialization(guards = "length >= 0")
        PSlice.SliceInfo doSliceObject(VirtualFrame frame, PObjectSlice slice, int length,
                        @Cached SliceExactCastToInt castStartNode,
                        @Cached SliceExactCastToInt castStopNode,
                        @Cached SliceExactCastToInt castStepNode) {
            Object startIn = castStartNode.execute(frame, slice.getStart());
            Object stopIn = castStopNode.execute(frame, slice.getStop());
            Object stepIn = castStepNode.execute(frame, slice.getStep());
            return PObjectSlice.computeIndices(startIn, stopIn, stepIn, length);
        }

        @Specialization(guards = "length < 0")
        PSlice.SliceInfo doSliceInt(@SuppressWarnings("unused") PSlice slice, @SuppressWarnings("unused") int length,
                        @Cached PRaiseNode raise) {
            throw raise.raise(ValueError, ErrorMessages.LENGTH_SHOULD_NOT_BE_NEG);
        }

        public static ComputeIndices create() {
            return SliceNodesFactory.ComputeIndicesNodeGen.create();
        }
    }

    /**
     * This is only applicable to slow path <i><b>internal</b></i> computations.
     */
    @GenerateUncached
    public abstract static class CoerceToObjectSlice extends PNodeWithContext {

        public abstract PObjectSlice execute(PSlice slice);

        @Specialization
        PObjectSlice doSliceInt(PIntSlice slice,
                        @Cached SliceCastToToBigInt start,
                        @Cached SliceCastToToBigInt stop,
                        @Cached SliceCastToToBigInt step,
                        @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start.execute(slice.getStart()), stop.execute(slice.getStop()), step.execute(slice.getStep()));
        }

        protected static boolean isBigInt(PObjectSlice slice) {
            return slice.getStart() instanceof BigInteger && slice.getStop() instanceof BigInteger && slice.getStep() instanceof BigInteger;
        }

        @Specialization(guards = "isBigInt(slice)")
        PObjectSlice doSliceObject(PObjectSlice slice) {
            return slice;
        }

        @Specialization
        PObjectSlice doSliceObject(PObjectSlice slice,
                        @Cached SliceCastToToBigInt start,
                        @Cached SliceCastToToBigInt stop,
                        @Cached SliceCastToToBigInt step,
                        @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start.execute(slice.getStart()), stop.execute(slice.getStop()), step.execute(slice.getStep()));
        }

        public static CoerceToObjectSlice create() {
            return SliceNodesFactory.CoerceToObjectSliceNodeGen.create();
        }
    }

    /**
     * This is only applicable to slow path <i><b>internal</b></i> computations.
     */
    @GenerateUncached
    public abstract static class CoerceToIntSlice extends PNodeWithContext {

        public abstract PSlice execute(PSlice slice);

        @Specialization
        PSlice doSliceInt(PIntSlice slice) {
            return slice;
        }

        @Specialization
        PSlice doSliceObject(PObjectSlice slice,
                        @Cached SliceLossyCastToInt start,
                        @Cached SliceLossyCastToInt stop,
                        @Cached SliceLossyCastToInt step,
                        @Cached PythonObjectFactory factory) {
            return factory.createObjectSlice(start.execute(slice.getStart()), stop.execute(slice.getStop()), step.execute(slice.getStep()));
        }

        public static CoerceToIntSlice create() {
            return SliceNodesFactory.CoerceToIntSliceNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class SliceUnpack extends PNodeWithContext {

        public abstract PSlice.SliceInfo execute(PSlice slice);

        @Specialization
        PSlice.SliceInfo doSliceInt(PIntSlice slice) {
            int start = slice.getIntStart();
            if (slice.isStartNone()) {
                start = slice.getIntStep() >= 0 ? 0 : Integer.MAX_VALUE;
            }
            return new PSlice.SliceInfo(start, slice.getIntStop(), slice.getIntStep());
        }

        @Specialization
        PSlice.SliceInfo doSliceObject(PObjectSlice slice,
                        @Cached SliceLossyCastToInt toInt,
                        @Cached PRaiseNode raiseNode) {
            /* this is harder to get right than you might think */
            int start, stop, step;
            if (slice.getStep() == PNone.NONE) {
                step = 1;
            } else {
                step = (int) toInt.execute(slice.getStep());
                if (step == 0) {
                    raiseNode.raise(ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
                }
            }

            if (slice.getStart() == PNone.NONE) {
                start = step < 0 ? Integer.MAX_VALUE : 0;
            } else {
                start = (int) toInt.execute(slice.getStart());
            }

            if (slice.getStop() == PNone.NONE) {
                stop = step < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            } else {
                stop = (int) toInt.execute(slice.getStop());
            }

            return new PSlice.SliceInfo(start, stop, step);
        }
    }

    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceCastToToBigInt extends Node {

        public abstract Object execute(Object x);

        @Specialization
        protected Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected Object doGeneric(Object i,
                        @Cached BranchProfile exceptionProfile,
                        @Cached PRaiseNode raise,
                        @Cached CastToJavaBigIntegerNode cast) {
            try {
                return cast.execute(i);
            } catch (PException e) {
                exceptionProfile.enter();
                throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
            }
        }
    }

    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceExactCastToInt extends Node {

        public abstract Object execute(Frame frame, Object x);

        @Specialization
        protected Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected Object doGeneric(Object i,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(i)) {
                return asSizeNode.executeExact(null, i);
            }
            throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
        }
    }

    @GenerateUncached
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceLossyCastToInt extends Node {

        public abstract Object execute(Object x);

        @Specialization
        protected Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected Object doGeneric(Object i,
                        @Cached BranchProfile exceptionProfile,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(i)) {
                return asSizeNode.executeLossy(null, i);
            }
            exceptionProfile.enter();
            throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);

        }
    }

    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class CastToSliceComponentNode extends PNodeWithContext {
        private final int defaultValue;
        private final int overflowValue;

        public CastToSliceComponentNode(int defaultValue, int overflowValue) {
            this.defaultValue = defaultValue;
            this.overflowValue = overflowValue;
        }

        public abstract int execute(VirtualFrame frame, int i);

        public abstract int execute(VirtualFrame frame, long i);

        public abstract int execute(VirtualFrame frame, Object i);

        @Specialization
        int doNone(@SuppressWarnings("unused") PNone i) {
            return defaultValue;
        }

        @Specialization
        int doBoolean(boolean i) {
            return PInt.intValue(i);
        }

        @Specialization
        int doInt(int i) {
            return i;
        }

        @Specialization
        int doLong(long i,
                        @Shared("indexErrorProfile") @Cached BranchProfile indexErrorProfile) {
            try {
                return PInt.intValueExact(i);
            } catch (OverflowException e) {
                indexErrorProfile.enter();
                return overflowValue;
            }
        }

        @Specialization
        int doPInt(PInt i,
                        @Shared("indexErrorProfile") @Cached BranchProfile indexErrorProfile) {
            try {
                return i.intValueExact();
            } catch (OverflowException e) {
                indexErrorProfile.enter();
                return overflowValue;
            }
        }

        @Specialization(guards = "!isPNone(i)", replaces = {"doBoolean", "doInt", "doLong", "doPInt"})
        int doGeneric(VirtualFrame frame, Object i,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            if (indexCheckNode.execute(i)) {
                try {
                    return asSizeNode.executeExact(frame, i);
                } catch (PException e) {
                    e.expect(PythonBuiltinClassType.OverflowError, errorProfile);
                    return overflowValue;
                }
            } else {
                throw raise.raise(TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
            }
        }

        public static CastToSliceComponentNode create(int defaultValue, int overflowValue) {
            return SliceNodesFactory.CastToSliceComponentNodeGen.create(defaultValue, overflowValue);
        }
    }
}
